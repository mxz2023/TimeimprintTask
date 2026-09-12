package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarTriggerProvider;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskTransitionRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the Trigger Planner logic:
 * scans calendar trigger bindings that are due for window extension and
 * generates new WAITING instances + planned READY signals.
 *
 * Per binding: lock definition → binding, calculate occurrences after cursor,
 * insert instances + signals, update cursor.
 */
@Repository
public class MxzTriggerPlannerPortImpl implements TriggerPlannerPort {

    private static final Logger log = LoggerFactory.getLogger(MxzTriggerPlannerPortImpl.class);

    private final TriggerBindingMapper bindingMapper;
    private final TaskDefinitionMapper definitionMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final ObjectMapper objectMapper;

    public MxzTriggerPlannerPortImpl(
            TriggerBindingMapper bindingMapper,
            TaskDefinitionMapper definitionMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            ObjectMapper objectMapper) {
        this.bindingMapper = bindingMapper;
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void planDueBindings(Instant now, int bindingBatch, int maxPerBinding) {
        var nowLdt = MxzStorageTime.toUtcLdt(now);
        // Find due bindings (next_fire_at <= now, not exhausted, binding_state = ACTIVE)
        List<Long> ids = bindingMapper.selectDueTriggerIds("calendar", nowLdt, bindingBatch);
        for (Long bindingId : ids) {
            try {
                planSingleBinding(bindingId, now, maxPerBinding);
            } catch (Exception e) {
                log.warn("Planner: error for binding {}: {}", bindingId, e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void planSingleBinding(long bindingId, Instant now, int maxPerBinding) {
        // Lock: definition → binding
        var binding = bindingMapper.selectByIdForUpdate(bindingId);
        if (binding == null || Boolean.TRUE.equals(binding.getExhausted())) return;
        if (!"ACTIVE".equals(binding.getBindingState())) return;

        var def = definitionMapper.selectByIdForUpdate(binding.getDefinitionId());
        if (def == null || !"ACTIVE".equals(def.getControlState())) return;

        long definitionId = def.getDefinitionId();
        long controlGen = def.getControlGeneration();
        long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
        String bindingKey = binding.getBindingKey();

        // Parse calendar rule
        Map<String, Object> configMap = parseMap(binding.getConfigJson());
        MxzCalendarOccurrenceCalculator.Rule rule = MxzCalendarConfigParser.parse(configMap);

        // Determine cursor: use nextFireAt as "already planned up to"
        Instant cursor = binding.getNextFireAt() != null
                ? MxzStorageTime.toInstant(binding.getNextFireAt()).minus(1, ChronoUnit.SECONDS)
                : now;

        // Calculate next occurrences after cursor, within 7-day window
        Instant windowEnd = now.plus(7, ChronoUnit.DAYS);
        var occurrences = MxzCalendarOccurrenceCalculator.preview(rule, cursor, maxPerBinding);

        int generated = 0;
        Instant lastOccurrence = null;
        for (var occ : occurrences) {
            if (occ.occurrenceAt().isAfter(windowEnd)) break;

            // Skip if signal already exists (idempotent)
            String signalKey = MxzCalendarTriggerProvider.signalKey(
                    definitionId, bindingKey, schedGen, controlGen, occ.occurrenceKey());
            var existing = signalMapper.selectBySourceKey(def.getTenantId(), "calendar", signalKey);
            if (existing != null) {
                lastOccurrence = occ.occurrenceAt();
                generated++;
                continue;
            }

            // Check if instance already exists (via unique key)
            String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
            TaskInstanceRow inst = new TaskInstanceRow();
            inst.setDefinitionId(definitionId);
            inst.setTriggerBindingId(bindingId);
            inst.setScheduleGeneration(schedGen);
            inst.setDefinitionControlGeneration(controlGen);
            inst.setOccurrenceKey(occ.occurrenceKey());
            inst.setOccurrenceAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setDueAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setLifecycleCategory("WAITING");
            inst.setScenarioState("PLANNED");
            inst.setScenarioSchemaVersion(def.getScenarioSchemaVersion());
            inst.setScenarioSnapshotJson(snapshotJson);
            inst.setSnapshotHash(MxzSha256.digestUtf8(snapshotJson));
            inst.setTitleSnapshot(def.getTitle());
            inst.setDescriptionSnapshot(def.getDescription());
            inst.setRevision(1L);
            inst.setCreatedAt(MxzStorageTime.toUtcLdt(now));
            inst.setUpdatedAt(MxzStorageTime.toUtcLdt(now));

            try {
                instanceMapper.insert(inst);
            } catch (Exception e) {
                // Unique key conflict: instance already exists, skip
                continue;
            }

            // Insert initial transition
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
            tr.setCreatedAt(MxzStorageTime.toUtcLdt(now));
            transitionMapper.insert(tr);

            // Insert planned signal
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
            signal.setOccurredAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            signal.setReceivedAt(MxzStorageTime.toUtcLdt(now));
            signal.setPayloadJson(payloadJson);
            signal.setPayloadHash(MxzSha256.digestUtf8(payloadJson));
            signal.setProcessStatus("READY");
            signal.setAttemptCount(0);
            signal.setMaxAttempts(8);
            signal.setNextAttemptAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            signal.setCreatedAt(MxzStorageTime.toUtcLdt(now));
            signal.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
            signalMapper.insert(signal);

            lastOccurrence = occ.occurrenceAt();
            generated++;
            if (generated >= maxPerBinding) break;
        }

        // Update cursor
        boolean exhausted = occurrences.isEmpty() || generated < maxPerBinding;
        if ("ONCE".equalsIgnoreCase(String.valueOf(configMap.get("type")))) {
            exhausted = true;
        }
        Instant nextFireAt = lastOccurrence != null ? lastOccurrence.plusSeconds(1) : null;
        bindingMapper.updateCursor(
                bindingId,
                binding.getRevision(),
                binding.getRevision() + 1,
                nextFireAt != null ? MxzStorageTime.toUtcLdt(nextFireAt) : null,
                "{}",
                exhausted,
                MxzStorageTime.toUtcLdt(now));
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildPayloadJson(MxzCalendarOccurrenceCalculator.Occurrence occ, String bindingKey) {
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
