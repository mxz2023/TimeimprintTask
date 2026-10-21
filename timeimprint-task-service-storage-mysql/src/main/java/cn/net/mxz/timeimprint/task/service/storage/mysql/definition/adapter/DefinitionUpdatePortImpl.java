package cn.net.mxz.timeimprint.task.service.storage.mysql.definition.adapter;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.definition.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.DefinitionUpdatePort;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigException;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarWindowedPreview;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarPlannedInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.provider.CalendarTriggerProvider;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.audit.mapper.AuditLogMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.mapper.TaskParticipantMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.audit.row.AuditLogRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.row.TaskTransitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.row.TriggerBindingRow;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow;

@Repository
public class DefinitionUpdatePortImpl implements DefinitionUpdatePort {

    private final TaskDefinitionMapper definitionMapper;
    private final TaskParticipantMapper participantMapper;
    private final TriggerBindingMapper bindingMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final AuditLogMapper auditLogMapper;
    private final DefinitionControlPort definitionControlPort;
    private final JsonMapper objectMapper;

    public DefinitionUpdatePortImpl(
            TaskDefinitionMapper definitionMapper,
            TaskParticipantMapper participantMapper,
            TriggerBindingMapper bindingMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            AuditLogMapper auditLogMapper,
            DefinitionControlPort definitionControlPort,
            JsonMapper objectMapper) {
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
            throw new ApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        if (def.getRevision() != cmd.expectedRevision()) {
            throw new ApplicationException("REVISION_CONFLICT", "definition revision mismatch");
        }
        if ("RETIRED".equals(def.getControlState())) {
            throw new ApplicationException("INVALID_STATE", "RETIRED cannot update");
        }

        var bindings = bindingMapper.selectByDefinitionId(cmd.definitionId());
        if (bindings.size() != 1) {
            throw new ApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        TriggerBindingRow binding = bindingMapper.selectByIdForUpdate(bindings.get(0).getTriggerBindingId());
        if (cmd.triggerBindings().size() != 1) {
            throw new ApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        var tb = cmd.triggerBindings().get(0);
        if (!"calendar".equals(tb.providerKey()) || !"primary".equals(tb.bindingKey())) {
            throw new ApplicationException("INVALID_REQUEST", "expected calendar/primary binding");
        }
        int currentSchema = binding.getSchemaVersion() == null ? 1 : binding.getSchemaVersion();
        int nextSchema = tb.schemaVersion();
        if (currentSchema == 3 && (nextSchema == 1 || nextSchema == 2)) {
            throw new ApplicationException("INVALID_REQUEST", "TW_DOWNGRADE：不能把 schemaVersion 3 改回更低版本");
        }
        boolean upgrade = (currentSchema == 1 || currentSchema == 2) && nextSchema == 3;
        if (!upgrade && nextSchema != currentSchema) {
            throw new ApplicationException("INVALID_REQUEST", "binding schemaVersion cannot change via update");
        }

        Map<String, Object> newConfigMap = parseMap(tb.configJson());
        final CalendarConfigParser.ParsedBinding parsed =
                parseBinding(newConfigMap, nextSchema, def.getScenarioKey());
        boolean equivalentUpgrade = false;
        if (upgrade && parsed.timeWindow() == null) {
            CalendarOccurrenceCalculator.Rule oldRule =
                    CalendarConfigParser.parse(parseMap(binding.getConfigJson()), currentSchema);
            equivalentUpgrade = oldRule.equals(parsed.rule());
        }
        boolean calendarChanged = !equivalentUpgrade && !jsonEquals(binding.getConfigJson(), tb.configJson());
        boolean contentChanged = contentChanged(def, cmd);
        boolean participantsChanged = participantsChanged(cmd.definitionId(), cmd.participants());
        if (!calendarChanged && !contentChanged && !participantsChanged) {
            return new UpdateResult(def.getRevision(), false, binding.getScheduleGeneration());
        }

        if (calendarChanged && parsed.timeWindow() != null) {
            try {
                CalendarWindowedPreview.requireWithinHorizon(parsed.rule(), parsed.timeWindow(), now);
            } catch (CalendarConfigException ex) {
                throw new ApplicationException(ex.errorCode(), ex.getMessage());
            }
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
                Sha256.digestUtf8(cmd.scenarioConfigJson()),
                cmd.actorId(),
                StorageTime.toUtcLdt(now));
        if (updated != 1) {
            throw new ApplicationException("REVISION_CONFLICT", "definition content update failed");
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
                row.setCreatedAt(StorageTime.toUtcLdt(now));
                row.setUpdatedAt(StorageTime.toUtcLdt(now));
                participantMapper.insert(row);
            }
        }

        long scheduleGeneration = binding.getScheduleGeneration() == null ? 1L : binding.getScheduleGeneration();
        if (calendarChanged) {
            scheduleGeneration = scheduleGeneration + 1;
            boolean active = "ACTIVE".equals(def.getControlState());
            List<CalendarOccurrenceCalculator.Occurrence> window = List.of();
            Instant nextFire;
            boolean exhausted;
            if (parsed.timeWindow() == null) {
                if (active) {
                    window = buildWindow(parsed.rule(), now, cmd.windowEnd(), newConfigMap);
                }
                if (!active) {
                    exhausted = true;
                    nextFire = window.isEmpty() ? null : window.get(0).occurrenceAt();
                } else if (CalendarOccurrenceCalculator.openEnded(parsed.rule())) {
                    var beyond = CalendarOccurrenceCalculator.preview(parsed.rule(), cmd.windowEnd(), 1);
                    exhausted = beyond.isEmpty();
                    nextFire = beyond.isEmpty()
                            ? (window.isEmpty() ? null : window.get(window.size() - 1).occurrenceAt())
                            : beyond.get(0).occurrenceAt();
                } else {
                    exhausted = window.isEmpty()
                            || "ONCE".equalsIgnoreCase(String.valueOf(newConfigMap.get("type")));
                    nextFire = window.isEmpty() ? null : window.get(0).occurrenceAt();
                }
            } else if (!active) {
                exhausted = true;
                nextFire = null;
            } else {
                var plan = CalendarWindowedPreview.planWrite(
                        parsed.rule(),
                        parsed.timeWindow(),
                        now,
                        cmd.windowEnd(),
                        "ONCE".equalsIgnoreCase(String.valueOf(newConfigMap.get("type"))));
                window = plan.inWindow();
                nextFire = plan.nextFireAt();
                exhausted = plan.exhausted();
            }
            int bindingUpdated = bindingMapper.updateConfigAndSchedule(
                    binding.getTriggerBindingId(),
                    binding.getRevision(),
                    binding.getRevision() + 1,
                    tb.configJson(),
                    Sha256.digestUtf8(tb.configJson()),
                    nextSchema,
                    scheduleGeneration,
                    StorageTime.toUtcLdt(nextFire),
                    "{}",
                    exhausted,
                    StorageTime.toUtcLdt(now));
            if (bindingUpdated != 1) {
                throw new ApplicationException("REVISION_CONFLICT", "binding update failed");
            }
            if (active) {
                materializeWindow(
                        cmd,
                        def.getControlGeneration(),
                        binding.getTriggerBindingId(),
                        scheduleGeneration,
                        window,
                        newConfigMap,
                        now);
            }
        } else if (contentChanged || participantsChanged) {
            if ("ACTIVE".equals(def.getControlState())) {
                instanceMapper.bumpWaitingSnapshots(
                        cmd.definitionId(), cmd.title(), cmd.description(), StorageTime.toUtcLdt(now));
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
        transition.setCreatedAt(StorageTime.toUtcLdt(now));
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
        audit.setCreatedAt(StorageTime.toUtcLdt(now));
        auditLogMapper.insert(audit);

        return new UpdateResult(newRevision, true, scheduleGeneration);
    }

    private boolean contentChanged(
            cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow def, UpdateCommand cmd) {
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
            List<cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand.ParticipantInput>
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

    private List<CalendarOccurrenceCalculator.Occurrence> buildWindow(
            CalendarOccurrenceCalculator.Rule rule,
            Instant now,
            Instant windowEnd,
            Map<String, Object> configMap) {
        var occurrences = CalendarOccurrenceCalculator.preview(rule, now, 100);
        List<CalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
        for (var occ : occurrences) {
            if (!occ.occurrenceAt().isAfter(windowEnd)) {
                window.add(occ);
            }
        }
        if (window.isEmpty() && "ONCE".equals(String.valueOf(configMap.get("type")).toUpperCase())) {
            throw new ApplicationException("INVALID_REQUEST", "ONCE must be strictly after update time");
        }
        return window;
    }

    private void materializeWindow(
            UpdateCommand cmd,
            long controlGeneration,
            long bindingId,
            long scheduleGeneration,
            List<CalendarOccurrenceCalculator.Occurrence> window,
            Map<String, Object> calendarConfig,
            Instant now) {
        var tb = cmd.triggerBindings().get(0);
        for (var occ : window) {
            String snapshotJson = CalendarPlannedInstanceSnapshot.json(occ.occurrenceKey(), calendarConfig);
            TaskInstanceRow inst = new TaskInstanceRow();
            inst.setDefinitionId(cmd.definitionId());
            inst.setTriggerBindingId(bindingId);
            inst.setScheduleGeneration(scheduleGeneration);
            inst.setDefinitionControlGeneration(controlGeneration);
            inst.setOccurrenceKey(occ.occurrenceKey());
            inst.setOccurrenceAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setDueAt(StorageTime.toUtcLdt(occ.occurrenceAt()));
            inst.setLifecycleCategory("WAITING");
            inst.setScenarioState("PLANNED");
            inst.setScenarioSchemaVersion(cmd.scenarioSchemaVersion());
            inst.setScenarioSnapshotJson(snapshotJson);
            inst.setSnapshotHash(Sha256.digestUtf8(snapshotJson));
            inst.setTitleSnapshot(cmd.title());
            inst.setDescriptionSnapshot(cmd.description());
            inst.setRevision(1L);
            inst.setCreatedAt(StorageTime.toUtcLdt(now));
            inst.setUpdatedAt(StorageTime.toUtcLdt(now));
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
            instTx.setCreatedAt(StorageTime.toUtcLdt(now));
            transitionMapper.insert(instTx);

            String signalKey = CalendarTriggerProvider.signalKey(
                    cmd.definitionId(), tb.bindingKey(), scheduleGeneration, controlGeneration, occ.occurrenceKey());
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(Map.of(
                        "occurrenceKey", occ.occurrenceKey(),
                        "occurrenceAt", occ.occurrenceAt().toString(),
                        "bindingKey", tb.bindingKey()));
            } catch (Exception e) {
                throw new ApplicationException("INTERNAL_ERROR", "signal payload");
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

    private CalendarConfigParser.ParsedBinding parseBinding(
            Map<String, Object> config, int schemaVersion, String scenarioKey) {
        try {
            return CalendarConfigParser.parseBinding(config, schemaVersion, scenarioKey);
        } catch (CalendarConfigException ex) {
            throw new ApplicationException(ex.errorCode(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new ApplicationException("INVALID_REQUEST", msg);
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
    }
}
