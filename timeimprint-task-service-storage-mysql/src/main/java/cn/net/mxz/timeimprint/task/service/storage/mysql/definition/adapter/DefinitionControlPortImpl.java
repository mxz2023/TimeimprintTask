package cn.net.mxz.timeimprint.task.service.storage.mysql.definition.adapter;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.definition.port.DefinitionControlPort;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Cancels WAITING instances/signals on pause/retire, and rebuilds the future
 * 7-day window on resume from the resume instant (no pause-interval backfill).
 */
@Repository
public class DefinitionControlPortImpl implements DefinitionControlPort {

    private final TaskDefinitionMapper definitionMapper;
    private final TriggerBindingMapper triggerMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final JsonMapper objectMapper;

    public DefinitionControlPortImpl(
            TaskDefinitionMapper definitionMapper,
            TriggerBindingMapper triggerMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            JsonMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void cancelWindowAndSignals(long definitionId, Instant now) {
        // Per 06: pause/retire txn ends WAITING + planned Signals only.
        // Unstarted Actions lose eligibility via controlGeneration; Worker cancels lazily.
        LocalDateTime nowLdt = StorageTime.toUtcLdt(now);
        String cancelledJson = "{\"scenarioState\":\"CANCELLED\"}";
        byte[] cancelledHash = Sha256.digestUtf8(cancelledJson);

        var bindings = triggerMapper.selectByDefinitionId(definitionId);
        for (var binding : bindings) {
            long bindingId = binding.getTriggerBindingId();
            long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();

            instanceMapper.cancelWaitingInstances(
                    definitionId,
                    bindingId,
                    schedGen,
                    "CANCELLED",
                    cancelledJson,
                    cancelledHash,
                    nowLdt,
                    nowLdt);

            signalMapper.ignoreByBindingGeneration(
                    definitionId, bindingId, schedGen, "CONTROL_STATE_CHANGE", nowLdt, nowLdt);
        }
    }

    @Override
    public void rebuildFutureWindow(long definitionId, Instant now) {
        var def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        if (!"ACTIVE".equals(def.getControlState())) {
            throw new ApplicationException("INVALID_STATE", "rebuild requires ACTIVE");
        }
        long controlGen = def.getControlGeneration() == null ? 1L : def.getControlGeneration();
        Instant windowEnd = now.plus(7, ChronoUnit.DAYS);

        var bindings = triggerMapper.selectByDefinitionId(definitionId);
        for (var bindingRow : bindings) {
            var binding = triggerMapper.selectByIdForUpdate(bindingRow.getTriggerBindingId());
            if (binding == null || !"calendar".equals(binding.getProviderKey())) {
                continue;
            }
            Map<String, Object> configMap = parseMap(binding.getConfigJson());
            final CalendarOccurrenceCalculator.Rule rule;
            try {
                rule = CalendarConfigParser.parse(configMap);
            } catch (IllegalArgumentException ex) {
                throw new ApplicationException("INVALID_REQUEST", ex.getMessage());
            }
            long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
            var occurrences = CalendarOccurrenceCalculator.preview(rule, now, 100);
            List<CalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
            for (var occ : occurrences) {
                if (!occ.occurrenceAt().isAfter(windowEnd)) {
                    window.add(occ);
                }
            }

            for (var occ : window) {
                materializeOne(
                        def.getTenantId(),
                        definitionId,
                        def.getScenarioSchemaVersion(),
                        def.getTitle(),
                        def.getDescription(),
                        binding.getTriggerBindingId(),
                        binding.getBindingKey(),
                        schedGen,
                        controlGen,
                        occ,
                        now);
            }

            Instant nextFire = window.isEmpty() ? null : window.get(0).occurrenceAt();
            boolean exhausted = window.isEmpty()
                    || "ONCE".equalsIgnoreCase(String.valueOf(configMap.get("type")));
            int updated = triggerMapper.updateCursor(
                    binding.getTriggerBindingId(),
                    binding.getRevision(),
                    binding.getRevision() + 1,
                    StorageTime.toUtcLdt(nextFire),
                    "{}",
                    exhausted,
                    StorageTime.toUtcLdt(now));
            if (updated != 1) {
                throw new ApplicationException("REVISION_CONFLICT", "binding cursor update failed on resume");
            }
        }
    }

    private void materializeOne(
            String tenantId,
            long definitionId,
            int scenarioSchemaVersion,
            String title,
            String description,
            long bindingId,
            String bindingKey,
            long scheduleGeneration,
            long controlGeneration,
            CalendarOccurrenceCalculator.Occurrence occ,
            Instant now) {
        String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
        TaskInstanceRow inst = new TaskInstanceRow();
        inst.setDefinitionId(definitionId);
        inst.setTriggerBindingId(bindingId);
        inst.setScheduleGeneration(scheduleGeneration);
        inst.setDefinitionControlGeneration(controlGeneration);
        inst.setOccurrenceKey(occ.occurrenceKey());
        inst.setOccurrenceAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
        inst.setDueAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
        inst.setLifecycleCategory("WAITING");
        inst.setScenarioState("PLANNED");
        inst.setScenarioSchemaVersion(scenarioSchemaVersion);
        inst.setScenarioSnapshotJson(snapshotJson);
        inst.setSnapshotHash(Sha256.digestUtf8(snapshotJson));
        inst.setTitleSnapshot(title);
        inst.setDescriptionSnapshot(description);
        inst.setRevision(1L);
        inst.setCreatedAt(StorageTime.toUtcLdt(now));
        inst.setUpdatedAt(StorageTime.toUtcLdt(now));
        instanceMapper.insert(inst);

        TaskTransitionRow instTx = new TaskTransitionRow();
        instTx.setDefinitionId(definitionId);
        instTx.setInstanceId(inst.getInstanceId());
        instTx.setSourceType("SYSTEM");
        instTx.setSourceKey("resume:" + occ.occurrenceKey());
        instTx.setCommandKey(null);
        instTx.setFromLifecycle(null);
        instTx.setToLifecycle("WAITING");
        instTx.setFromScenarioState(null);
        instTx.setToScenarioState("PLANNED");
        instTx.setFromRevision(0L);
        instTx.setToRevision(1L);
        instTx.setActorType("SYSTEM");
        instTx.setActorId("resume");
        instTx.setSummaryJson("{\"event\":\"materialize_waiting\",\"reason\":\"resume\"}");
        instTx.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        instTx.setCreatedAt(StorageTime.toUtcLdt(now));
        transitionMapper.insert(instTx);

        String signalKey = CalendarTriggerProvider.signalKey(
                definitionId, bindingKey, scheduleGeneration, controlGeneration, occ.occurrenceKey());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "occurrenceKey", occ.occurrenceKey(),
                    "occurrenceAt", occ.occurrenceAt().toString(),
                    "bindingKey", bindingKey));
        } catch (Exception e) {
            throw new ApplicationException("INTERNAL_ERROR", "signal payload");
        }
        TaskSignalRow signal = new TaskSignalRow();
        signal.setTenantId(tenantId);
        signal.setDefinitionId(definitionId);
        signal.setTriggerBindingId(bindingId);
        signal.setInstanceId(inst.getInstanceId());
        signal.setDefinitionControlGeneration(controlGeneration);
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
        signalMapper.insert(signal);
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
    }
}
