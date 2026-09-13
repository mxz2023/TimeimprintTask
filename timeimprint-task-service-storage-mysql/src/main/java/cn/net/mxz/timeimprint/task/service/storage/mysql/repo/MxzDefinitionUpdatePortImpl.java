package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionUpdatePort;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarTriggerProvider;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzStorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.AuditLogMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskParticipantMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.AuditLogRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskTransitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TriggerBindingRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class MxzDefinitionUpdatePortImpl implements DefinitionUpdatePort {

    private final TaskDefinitionMapper definitionMapper;
    private final TaskParticipantMapper participantMapper;
    private final TriggerBindingMapper bindingMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final AuditLogMapper auditLogMapper;
    private final DefinitionControlPort definitionControlPort;
    private final ObjectMapper objectMapper;

    public MxzDefinitionUpdatePortImpl(
            TaskDefinitionMapper definitionMapper,
            TaskParticipantMapper participantMapper,
            TriggerBindingMapper bindingMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            AuditLogMapper auditLogMapper,
            DefinitionControlPort definitionControlPort,
            ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.participantMapper = participantMapper;
        this.bindingMapper = bindingMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.auditLogMapper = auditLogMapper;
        this.definitionControlPort = definitionControlPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public UpdateResult apply(UpdateCommand cmd) {
        Instant now = cmd.now();
        var def = definitionMapper.selectByIdForUpdate(cmd.definitionId());
        if (def == null) {
            throw new MxzApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        if (def.getRevision() != cmd.expectedRevision()) {
            throw new MxzApplicationException("REVISION_CONFLICT", "definition revision mismatch");
        }
        if ("RETIRED".equals(def.getControlState())) {
            throw new MxzApplicationException("INVALID_STATE", "RETIRED cannot update");
        }

        var bindings = bindingMapper.selectByDefinitionId(cmd.definitionId());
        if (bindings.size() != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        TriggerBindingRow binding = bindingMapper.selectByIdForUpdate(bindings.get(0).getTriggerBindingId());
        if (cmd.triggerBindings().size() != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        var tb = cmd.triggerBindings().get(0);
        if (!"calendar".equals(tb.providerKey()) || !"primary".equals(tb.bindingKey())) {
            throw new MxzApplicationException("INVALID_REQUEST", "expected calendar/primary binding");
        }
        if (tb.schemaVersion() != binding.getSchemaVersion()) {
            throw new MxzApplicationException("INVALID_REQUEST", "binding schemaVersion cannot change via update");
        }

        Map<String, Object> newConfigMap = parseMap(tb.configJson());
        final MxzCalendarOccurrenceCalculator.Rule rule;
        try {
            rule = MxzCalendarConfigParser.parse(newConfigMap);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new MxzApplicationException("INVALID_REQUEST", msg);
        }

        boolean calendarChanged = !jsonEquals(binding.getConfigJson(), tb.configJson());
        boolean contentChanged = contentChanged(def, cmd);
        boolean participantsChanged = participantsChanged(cmd.definitionId(), cmd.participants());
        if (!calendarChanged && !contentChanged && !participantsChanged) {
            return new UpdateResult(def.getRevision(), false, binding.getScheduleGeneration());
        }

        if (calendarChanged) {
            definitionControlPort.cancelWindowAndSignals(cmd.definitionId(), now);
        }

        long newRevision = def.getRevision() + 1;
        int updated = definitionMapper.updateContent(
                cmd.definitionId(),
                cmd.expectedRevision(),
                newRevision,
                cmd.scenarioSchemaVersion(),
                cmd.title(),
                cmd.description(),
                cmd.scenarioConfigJson(),
                MxzSha256.digestUtf8(cmd.scenarioConfigJson()),
                cmd.actorId(),
                MxzStorageTime.toUtcLdt(now));
        if (updated != 1) {
            throw new MxzApplicationException("REVISION_CONFLICT", "definition content update failed");
        }

        if (participantsChanged) {
            participantMapper.deleteByDefinitionIdDefinitionLevel(cmd.definitionId());
            for (var p : cmd.participants()) {
                TaskParticipantRow row = new TaskParticipantRow();
                row.setDefinitionId(cmd.definitionId());
                row.setInstanceId(null);
                row.setPrincipalType(p.principalType());
                row.setPrincipalId(p.principalId());
                row.setRoleCode(p.roleCode());
                row.setSourceCode("REQUEST");
                row.setMetadataJson("{}");
                row.setCreatedAt(MxzStorageTime.toUtcLdt(now));
                row.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
                participantMapper.insert(row);
            }
        }

        long scheduleGeneration = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
        if (calendarChanged) {
            scheduleGeneration = scheduleGeneration + 1;
            boolean active = "ACTIVE".equals(def.getControlState());
            List<MxzCalendarOccurrenceCalculator.Occurrence> window = List.of();
            if (active) {
                window = buildWindow(rule, now, cmd.windowEnd(), newConfigMap);
            }
            Instant nextFire = window.isEmpty() ? null : window.get(0).occurrenceAt();
            boolean exhausted = !active
                    || window.isEmpty()
                    || "ONCE".equalsIgnoreCase(String.valueOf(newConfigMap.get("type")));
            int bindingUpdated = bindingMapper.updateConfigAndSchedule(
                    binding.getTriggerBindingId(),
                    binding.getRevision(),
                    binding.getRevision() + 1,
                    tb.configJson(),
                    MxzSha256.digestUtf8(tb.configJson()),
                    scheduleGeneration,
                    MxzStorageTime.toUtcLdt(nextFire),
                    "{}",
                    exhausted,
                    MxzStorageTime.toUtcLdt(now));
            if (bindingUpdated != 1) {
                throw new MxzApplicationException("REVISION_CONFLICT", "binding update failed");
            }
            if (active) {
                materializeWindow(
                        cmd,
                        def.getControlGeneration(),
                        binding.getTriggerBindingId(),
                        scheduleGeneration,
                        window,
                        now);
            }
        } else if (contentChanged || participantsChanged) {
            if ("ACTIVE".equals(def.getControlState())) {
                instanceMapper.bumpWaitingSnapshots(
                        cmd.definitionId(), cmd.title(), cmd.description(), MxzStorageTime.toUtcLdt(now));
            }
        }

        TaskTransitionRow transition = new TaskTransitionRow();
        transition.setDefinitionId(cmd.definitionId());
        transition.setInstanceId(null);
        transition.setSourceType("COMMAND");
        transition.setSourceKey(cmd.requestId());
        transition.setCommandKey("update");
        transition.setFromControlState(def.getControlState());
        transition.setToControlState(def.getControlState());
        transition.setFromRevision(cmd.expectedRevision());
        transition.setToRevision(newRevision);
        transition.setActorType(cmd.actorType());
        transition.setActorId(cmd.actorId());
        transition.setSummaryJson("{\"event\":\"update\",\"calendarChanged\":" + calendarChanged + "}");
        transition.setTraceId(cmd.traceId());
        transition.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        transitionMapper.insert(transition);

        AuditLogRow audit = new AuditLogRow();
        audit.setTenantId(cmd.tenantId());
        audit.setResourceType("DEFINITION");
        audit.setResourceId(String.valueOf(cmd.definitionId()));
        audit.setDefinitionId(cmd.definitionId());
        audit.setTransitionId(transition.getTransitionId());
        audit.setEventType("UPDATE");
        audit.setActorType(cmd.actorType());
        audit.setActorId(cmd.actorId());
        audit.setTraceId(cmd.traceId());
        audit.setDetailJson("{\"calendarChanged\":" + calendarChanged + ",\"scheduleGeneration\":" + scheduleGeneration + "}");
        audit.setCreatedAt(MxzStorageTime.toUtcLdt(now));
        auditLogMapper.insert(audit);

        return new UpdateResult(newRevision, true, scheduleGeneration);
    }

    private boolean contentChanged(
            cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskDefinitionRow def, UpdateCommand cmd) {
        if (def.getScenarioSchemaVersion() != cmd.scenarioSchemaVersion()) {
            return true;
        }
        if (!Objects.equals(def.getTitle(), cmd.title())) {
            return true;
        }
        if (!Objects.equals(def.getDescription(), cmd.description())) {
            return true;
        }
        return !jsonEquals(def.getScenarioConfigJson(), cmd.scenarioConfigJson());
    }

    private boolean participantsChanged(
            long definitionId,
            List<cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand.ParticipantInput>
                    next) {
        var current = participantMapper.selectByDefinitionId(definitionId, null);
        if (current.size() != next.size()) {
            return true;
        }
        List<String> currentKeys = current.stream()
                .map(c -> c.getRoleCode() + "|" + c.getPrincipalType() + "|" + c.getPrincipalId())
                .sorted()
                .toList();
        List<String> nextKeys = next.stream()
                .map(n -> n.roleCode() + "|" + n.principalType() + "|" + n.principalId())
                .sorted()
                .toList();
        return !currentKeys.equals(nextKeys);
    }

    private List<MxzCalendarOccurrenceCalculator.Occurrence> buildWindow(
            MxzCalendarOccurrenceCalculator.Rule rule,
            Instant now,
            Instant windowEnd,
            Map<String, Object> configMap) {
        var occurrences = MxzCalendarOccurrenceCalculator.preview(rule, now, 100);
        List<MxzCalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
        for (var occ : occurrences) {
            if (!occ.occurrenceAt().isAfter(windowEnd)) {
                window.add(occ);
            }
        }
        if (window.isEmpty() && "ONCE".equals(String.valueOf(configMap.get("type")).toUpperCase())) {
            throw new MxzApplicationException("INVALID_REQUEST", "ONCE must be strictly after update time");
        }
        return window;
    }

    private void materializeWindow(
            UpdateCommand cmd,
            long controlGeneration,
            long bindingId,
            long scheduleGeneration,
            List<MxzCalendarOccurrenceCalculator.Occurrence> window,
            Instant now) {
        var tb = cmd.triggerBindings().get(0);
        for (var occ : window) {
            String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
            TaskInstanceRow inst = new TaskInstanceRow();
            inst.setDefinitionId(cmd.definitionId());
            inst.setTriggerBindingId(bindingId);
            inst.setScheduleGeneration(scheduleGeneration);
            inst.setDefinitionControlGeneration(controlGeneration);
            inst.setOccurrenceKey(occ.occurrenceKey());
            inst.setOccurrenceAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setDueAt(MxzStorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setLifecycleCategory("WAITING");
            inst.setScenarioState("PLANNED");
            inst.setScenarioSchemaVersion(cmd.scenarioSchemaVersion());
            inst.setScenarioSnapshotJson(snapshotJson);
            inst.setSnapshotHash(MxzSha256.digestUtf8(snapshotJson));
            inst.setTitleSnapshot(cmd.title());
            inst.setDescriptionSnapshot(cmd.description());
            inst.setRevision(1L);
            inst.setCreatedAt(MxzStorageTime.toUtcLdt(now));
            inst.setUpdatedAt(MxzStorageTime.toUtcLdt(now));
            instanceMapper.insert(inst);

            TaskTransitionRow instTx = new TaskTransitionRow();
            instTx.setDefinitionId(cmd.definitionId());
            instTx.setInstanceId(inst.getInstanceId());
            instTx.setSourceType("SYSTEM");
            instTx.setSourceKey("window:" + occ.occurrenceKey());
            instTx.setCommandKey(null);
            instTx.setFromLifecycle(null);
            instTx.setToLifecycle("WAITING");
            instTx.setFromScenarioState(null);
            instTx.setToScenarioState("PLANNED");
            instTx.setFromRevision(0L);
            instTx.setToRevision(1L);
            instTx.setActorType("SYSTEM");
            instTx.setActorId("planner");
            instTx.setSummaryJson("{\"event\":\"materialize_waiting\"}");
            instTx.setTraceId(cmd.traceId());
            instTx.setCreatedAt(MxzStorageTime.toUtcLdt(now));
            transitionMapper.insert(instTx);

            String signalKey = MxzCalendarTriggerProvider.signalKey(
                    cmd.definitionId(), tb.bindingKey(), scheduleGeneration, controlGeneration, occ.occurrenceKey());
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(Map.of(
                        "occurrenceKey", occ.occurrenceKey(),
                        "occurrenceAt", occ.occurrenceAt().toString(),
                        "bindingKey", tb.bindingKey()));
            } catch (Exception e) {
                throw new MxzApplicationException("INTERNAL_ERROR", "signal payload");
            }
            TaskSignalRow signal = new TaskSignalRow();
            signal.setTenantId(cmd.tenantId());
            signal.setDefinitionId(cmd.definitionId());
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
    }

    private boolean jsonEquals(String leftJson, String rightJson) {
        try {
            JsonNode left = objectMapper.readTree(leftJson == null ? "null" : leftJson);
            JsonNode right = objectMapper.readTree(rightJson == null ? "null" : rightJson);
            return left.equals(right);
        } catch (Exception e) {
            return Objects.equals(leftJson, rightJson);
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
    }
}
