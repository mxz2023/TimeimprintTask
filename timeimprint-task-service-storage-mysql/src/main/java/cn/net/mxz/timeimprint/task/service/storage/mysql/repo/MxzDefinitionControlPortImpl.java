package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
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
public class MxzDefinitionControlPortImpl implements DefinitionControlPort {

    private final TaskDefinitionMapper definitionMapper;
    private final TriggerBindingMapper triggerMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final ObjectMapper objectMapper;

    public MxzDefinitionControlPortImpl(
            TaskDefinitionMapper definitionMapper,
            TriggerBindingMapper triggerMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void cancelWindowAndSignals(long definitionId, Instant now) {
        LocalDateTime nowLdt = MxzStorageTime.toUtcLdt(now);
        String cancelledJson = "{\"scenarioState\":\"CANCELLED\"}";
        byte[] cancelledHash = MxzSha256.digestUtf8(cancelledJson);

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
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        if (!"ACTIVE".equals(def.getControlState())) {
            throw new MxzApplicationException("INVALID_STATE", "rebuild requires ACTIVE");
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
            final MxzCalendarOccurrenceCalculator.Rule rule;
            try {
                rule = MxzCalendarConfigParser.parse(configMap);
            } catch (IllegalArgumentException ex) {
                throw new MxzApplicationException("INVALID_REQUEST", ex.getMessage());
            }
            long schedGen = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
            var occurrences = MxzCalendarOccurrenceCalculator.preview(rule, now, 100);
            List<MxzCalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
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
                    MxzStorageTime.toUtcLdt(nextFire),
                    "{}",
                    exhausted,
                    MxzStorageTime.toUtcLdt(now));
            if (updated != 1) {
                throw new MxzApplicationException("REVISION_CONFLICT", "binding cursor update failed on resume");
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
            MxzCalendarOccurrenceCalculator.Occurrence occ,
            Instant now) {
        String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
        TaskInstanceRow inst = new TaskInstanceRow();
        inst.setDefinitionId(definitionId);
        inst.setTriggerBindingId(bindingId);
        inst.setScheduleGeneration(scheduleGeneration);
        inst.setDefinitionControlGeneration(controlGeneration);
        inst.setOccurrenceKey(occ.occurrenceKey());
        inst.setOccurrenceAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
        inst.setDueAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
        inst.setLifecycleCategory("WAITING");
        inst.setScenarioState("PLANNED");
        inst.setScenarioSchemaVersion(scenarioSchemaVersion);
        inst.setScenarioSnapshotJson(snapshotJson);
        inst.setSnapshotHash(MxzSha256.digestUtf8(snapshotJson));
        inst.setTitleSnapshot(title);
        inst.setDescriptionSnapshot(description);
        inst.setRevision(1L);
        inst.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        inst.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
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
        instTx.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        transitionMapper.insert(instTx);

        String signalKey = MxzCalendarTriggerProvider.signalKey(
                definitionId, bindingKey, scheduleGeneration, controlGeneration, occ.occurrenceKey());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "occurrenceKey", occ.occurrenceKey(),
                    "occurrenceAt", occ.occurrenceAt().toString(),
                    "bindingKey", bindingKey));
        } catch (Exception e) {
            throw new MxzApplicationException("INTERNAL_ERROR", "signal payload");
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
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
    }
}
