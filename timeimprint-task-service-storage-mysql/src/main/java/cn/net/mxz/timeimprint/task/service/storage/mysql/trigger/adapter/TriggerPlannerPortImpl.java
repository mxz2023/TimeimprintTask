package cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.adapter;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.provider.CalendarTriggerProvider;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.row.TaskTransitionRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Trigger Planner: unlocked candidate scan, then per-binding short TX with
 * definition → trigger binding lock order (06). Self-invocation of
 * {@code @Transactional} is avoided via an explicit REQUIRES_NEW template (A03).
 */
@Repository
public class TriggerPlannerPortImpl implements TriggerPlannerPort {

    private static final Logger log = LoggerFactory.getLogger(TriggerPlannerPortImpl.class);

    private final TriggerBindingMapper bindingMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public TriggerPlannerPortImpl(
            TriggerBindingMapper bindingMapper,
            TaskDefinitionMapper definitionMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.bindingMapper = bindingMapper;
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void planDueBindings(Instant now, int bindingBatch, int maxPerBinding) {
        var nowLdt = StorageTime.toUtcLdt(now);
        // Unlocked / auto-commit candidate scan; each binding gets its own short TX (06).
        List<Long> ids = bindingMapper.selectDueTriggerIds("calendar", nowLdt, bindingBatch);
        for (Long bindingId : ids) {
            try {
                planBinding(bindingId, now, maxPerBinding);
            } catch (Exception e) {
                log.warn("Planner: error for binding {}: {}", bindingId, e.getMessage());
            }
        }
    }

    @Override
    public void planBinding(long triggerBindingId, Instant now, int maxPerBinding) {
        requiresNew.executeWithoutResult(status -> planOneLocked(triggerBindingId, now, maxPerBinding));
    }

    private void planOneLocked(long bindingId, Instant now, int maxPerBinding) {
        // Peek binding without lock to learn definitionId, then lock definition → binding (06).
        var peek = bindingMapper.selectById(bindingId);
        if (peek == null || Boolean.TRUE.equals(peek.getExhausted())) {
            return;
        }
        if (!"ACTIVE".equals(peek.getBindingState())) {
            return;
        }

        var def = definitionMapper.selectByIdForUpdate(peek.getDefinitionId());
        if (def == null || !"ACTIVE".equals(def.getControlState())) {
            return;
        }

        var binding = bindingMapper.selectByIdForUpdate(bindingId);
        if (binding == null || Boolean.TRUE.equals(binding.getExhausted())) {
            return;
        }
        if (!"ACTIVE".equals(binding.getBindingState())) {
            return;
        }
        if (!binding.getDefinitionId().equals(def.getDefinitionId())) {
            return;
        }

        long definitionId = def.getDefinitionId();
        long controlGen = def.getControlGeneration();
        long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
        String bindingKey = binding.getBindingKey();

        Map<String, Object> configMap = parseMap(binding.getConfigJson());
        CalendarOccurrenceCalculator.Rule rule = CalendarConfigParser.parse(configMap);

        Instant cursor = binding.getNextFireAt() != null
                ? StorageTime.toInstant(binding.getNextFireAt()).minus(1, ChronoUnit.SECONDS)
                : now;

        Instant windowEnd = now.plus(7, ChronoUnit.DAYS);
        var occurrences = CalendarOccurrenceCalculator.preview(rule, cursor, maxPerBinding);

        int generated = 0;
        Instant lastOccurrence = null;
        for (var occ : occurrences) {
            if (occ.occurrenceAt().isAfter(windowEnd)) {
                break;
            }

            String signalKey = CalendarTriggerProvider.signalKey(
                    definitionId, bindingKey, schedGen, controlGen, occ.occurrenceKey());
            var existing = signalMapper.selectBySourceKey(def.getTenantId(), "calendar", signalKey);
            if (existing != null) {
                lastOccurrence = occ.occurrenceAt();
                generated++;
                continue;
            }

            String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
            TaskInstanceRow inst = new TaskInstanceRow();
            inst.setDefinitionId(definitionId);
            inst.setTriggerBindingId(bindingId);
            inst.setScheduleGeneration(schedGen);
            inst.setDefinitionControlGeneration(controlGen);
            inst.setOccurrenceKey(occ.occurrenceKey());
            inst.setOccurrenceAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setDueAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setLifecycleCategory("WAITING");
            inst.setScenarioState("PLANNED");
            inst.setScenarioSchemaVersion(def.getScenarioSchemaVersion());
            inst.setScenarioSnapshotJson(snapshotJson);
            inst.setSnapshotHash(Sha256.digestUtf8(snapshotJson));
            inst.setTitleSnapshot(def.getTitle());
            inst.setDescriptionSnapshot(def.getDescription());
            inst.setRevision(1L);
            inst.setCreatedAt(StorageTime.toUtcLdt(now));
            inst.setUpdatedAt(StorageTime.toUtcLdt(now));

            try {
                instanceMapper.insert(inst);
            } catch (DataIntegrityViolationException e) {
                // Concurrent planner lost the unique race; treat as already planned.
                lastOccurrence = occ.occurrenceAt();
                generated++;
                continue;
            }

            var tr = new TaskTransitionRow();
            tr.setDefinitionId(definitionId);
            tr.setInstanceId(inst.getInstanceId());
            tr.setSourceType("SYSTEM");
            tr.setSourceKey("planner:" + occ.occurrenceKey());
            tr.setFromLifecycle(null);
            tr.setToLifecycle("WAITING");
            tr.setFromScenarioState(null);
            tr.setToScenarioState("PLANNED");
            tr.setFromRevision(0L);
            tr.setToRevision(1L);
            tr.setActorType("SYSTEM");
            tr.setActorId("planner");
            tr.setSummaryJson("{\"event\":\"materialize_waiting\"}");
            tr.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
            tr.setCreatedAt(StorageTime.toUtcLdt(now));
            transitionMapper.insert(tr);

            String payloadJson = buildPayloadJson(occ, bindingKey);
            TaskSignalRow signal = new TaskSignalRow();
            signal.setTenantId(def.getTenantId());
            signal.setDefinitionId(definitionId);
            signal.setTriggerBindingId(bindingId);
            signal.setInstanceId(inst.getInstanceId());
            signal.setDefinitionControlGeneration(controlGen);
            signal.setParentSignalId(null);
            signal.setRedriveNo(0);
            signal.setProviderKey("calendar");
            signal.setSignalKey(signalKey);
            signal.setSchemaVersion(1);
            signal.setOccurredAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            signal.setReceivedAt(StorageTime.toUtcLdt(now));
            signal.setPayloadJson(payloadJson);
            signal.setPayloadHash(Sha256.digestUtf8(payloadJson));
            signal.setProcessStatus("READY");
            signal.setAttemptCount(0);
            signal.setMaxAttempts(8);
            signal.setNextAttemptAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            signal.setCreatedAt(StorageTime.toUtcLdt(now));
            signal.setUpdatedAt(StorageTime.toUtcLdt(now));
            try {
                signalMapper.insert(signal);
            } catch (DataIntegrityViolationException e) {
                // Instance inserted but signal raced; unique key guarantees single signal.
                lastOccurrence = occ.occurrenceAt();
                generated++;
                continue;
            }

            lastOccurrence = occ.occurrenceAt();
            generated++;
            if (generated >= maxPerBinding) {
                break;
            }
        }

        boolean exhausted = occurrences.isEmpty() || generated < maxPerBinding;
        if ("ONCE".equalsIgnoreCase(String.valueOf(configMap.get("type")))) {
            exhausted = true;
        }
        Instant nextFireAt = lastOccurrence != null ? lastOccurrence.plusSeconds(1) : null;
        bindingMapper.updateCursor(
                bindingId,
                binding.getRevision(),
                binding.getRevision() + 1,
                nextFireAt != null ? StorageTime.toUtcLdt(nextFireAt) : null,
                "{}",
                exhausted,
                StorageTime.toUtcLdt(now));
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildPayloadJson(CalendarOccurrenceCalculator.Occurrence occ, String bindingKey) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "occurrenceKey", occ.occurrenceKey(),
                    "occurrenceAt", occ.occurrenceAt().toString(),
                    "bindingKey", bindingKey));
        } catch (Exception e) {
            return "{}";
        }
    }
}
