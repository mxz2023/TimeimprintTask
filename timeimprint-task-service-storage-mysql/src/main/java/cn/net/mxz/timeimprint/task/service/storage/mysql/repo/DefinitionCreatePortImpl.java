package cn.net.mxz.timeimprint.task.service.storage.mysql.repo;

import cn.net.mxz.timeimprint.task.common.Sha256;
import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.model.CreatedDefinitionResult;
import cn.net.mxz.timeimprint.task.service.application.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.model.TriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionCreatePort;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarTriggerProvider;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.storage.mysql.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.StorageTime;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.AuditLogMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskParticipantMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.AuditLogRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskTransitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TriggerBindingRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class DefinitionCreatePortImpl implements DefinitionCreatePort {

    private final TaskDefinitionMapper definitionMapper;
    private final TaskParticipantMapper participantMapper;
    private final TriggerBindingMapper bindingMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public DefinitionCreatePortImpl(
            TaskDefinitionMapper definitionMapper,
            TaskParticipantMapper participantMapper,
            TriggerBindingMapper bindingMapper,
            TaskInstanceMapper instanceMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            AuditLogMapper auditLogMapper,
            ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.participantMapper = participantMapper;
        this.bindingMapper = bindingMapper;
        this.instanceMapper = instanceMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreatedDefinitionResult createActive(CreateDefinitionCommand cmd) {
        Instant now = cmd.now();
        TaskDefinitionRow def = new TaskDefinitionRow();
        def.setTenantId(cmd.tenantId());
        def.setScenarioKey(cmd.scenarioKey());
        def.setScenarioSchemaVersion(cmd.scenarioSchemaVersion());
        def.setTitle(cmd.title());
        def.setDescription(cmd.description());
        def.setScenarioConfigJson(cmd.scenarioConfigJson());
        def.setScenarioConfigHash(Sha256.digestUtf8(cmd.scenarioConfigJson()));
        def.setControlState("ACTIVE");
        def.setControlGeneration(1L);
        def.setRevision(1L);
        def.setCreatedBy(cmd.actorId());
        def.setUpdatedBy(cmd.actorId());
        def.setCreatedAt(StorageTime.toUtcLdt(now));
        def.setUpdatedAt(StorageTime.toUtcLdt(now));
        definitionMapper.insert(def);
        long definitionId = def.getDefinitionId();

        List<ParticipantRecord> participants = new ArrayList<>();
        for (var p : cmd.participants()) {
            TaskParticipantRow row = new TaskParticipantRow();
            row.setDefinitionId(definitionId);
            row.setInstanceId(null);
            row.setPrincipalType(p.principalType());
            row.setPrincipalId(p.principalId());
            row.setRoleCode(p.roleCode());
            row.setSourceCode("REQUEST");
            row.setMetadataJson("{}");
            row.setCreatedAt(StorageTime.toUtcLdt(now));
            row.setUpdatedAt(StorageTime.toUtcLdt(now));
            participantMapper.insert(row);
            participants.add(RowMapper.toParticipant(row));
        }

        if (cmd.triggerBindings().size() != 1) {
            throw new ApplicationException("INVALID_REQUEST", "S01 requires exactly one trigger binding");
        }
        var tb = cmd.triggerBindings().get(0);
        if (!"calendar".equals(tb.providerKey()) || !"primary".equals(tb.bindingKey())) {
            throw new ApplicationException("INVALID_REQUEST", "expected calendar/primary binding");
        }
        Map<String, Object> configMap = parseMap(tb.configJson());
        final cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Rule rule;
        try {
            rule = CalendarConfigParser.parse(configMap);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new ApplicationException("INVALID_REQUEST", msg);
        }
        var occurrences = CalendarOccurrenceCalculator.preview(rule, now, 100);
        List<CalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
        for (var occ : occurrences) {
            if (!occ.occurrenceAt().isAfter(cmd.windowEnd())) {
                window.add(occ);
            }
        }
        if (window.isEmpty() && "ONCE".equals(String.valueOf(configMap.get("type")).toUpperCase())) {
            throw new ApplicationException("INVALID_REQUEST", "ONCE must be strictly after create time");
        }

        TriggerBindingRow binding = new TriggerBindingRow();
        binding.setDefinitionId(definitionId);
        binding.setBindingKey(tb.bindingKey());
        binding.setProviderKey(tb.providerKey());
        binding.setSchemaVersion(tb.schemaVersion());
        binding.setConfigJson(tb.configJson());
        binding.setConfigHash(Sha256.digestUtf8(tb.configJson()));
        binding.setBindingState("ACTIVE");
        binding.setScheduleGeneration(1L);
        Instant nextFire = window.isEmpty() ? null : window.get(0).occurrenceAt();
        binding.setNextFireAt(StorageTime.toUtcLdt(nextFire));
        binding.setCursorJson("{}");
        binding.setExhausted(window.isEmpty() || "ONCE".equalsIgnoreCase(String.valueOf(configMap.get("type"))));
        binding.setRevision(1L);
        binding.setCreatedAt(StorageTime.toUtcLdt(now));
        binding.setUpdatedAt(StorageTime.toUtcLdt(now));
        bindingMapper.insert(binding);
        long bindingId = binding.getTriggerBindingId();

        TaskTransitionRow transition = new TaskTransitionRow();
        transition.setDefinitionId(definitionId);
        transition.setInstanceId(null);
        transition.setSourceType("COMMAND");
        transition.setSourceKey(cmd.requestId());
        transition.setCommandKey("create");
        transition.setFromControlState(null);
        transition.setToControlState("ACTIVE");
        transition.setFromRevision(0L);
        transition.setToRevision(1L);
        transition.setActorType(cmd.actorType());
        transition.setActorId(cmd.actorId());
        transition.setSummaryJson("{\"event\":\"create\"}");
        transition.setTraceId(cmd.traceId() == null ? cmd.requestId().replace("-", "") : cmd.traceId());
        transition.setCreatedAt(StorageTime.toUtcLdt(now));
        transitionMapper.insert(transition);

        List<TaskInstanceSnapshot> instances = new ArrayList<>();
        int signalCount = 0;
        for (var occ : window) {
            String snapshotJson = "{\"occurrenceKey\":\"" + occ.occurrenceKey() + "\"}";
            TaskInstanceRow inst = new TaskInstanceRow();
            inst.setDefinitionId(definitionId);
            inst.setTriggerBindingId(bindingId);
            inst.setScheduleGeneration(1L);
            inst.setDefinitionControlGeneration(1L);
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
            instTx.setDefinitionId(definitionId);
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
            instTx.setTraceId(transition.getTraceId());
            instTx.setCreatedAt(StorageTime.toUtcLdt(now));
            transitionMapper.insert(instTx);

            String signalKey = CalendarTriggerProvider.signalKey(
                    definitionId, tb.bindingKey(), 1L, 1L, occ.occurrenceKey());
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
            signal.setDefinitionId(definitionId);
            signal.setTriggerBindingId(bindingId);
            signal.setInstanceId(inst.getInstanceId());
            signal.setDefinitionControlGeneration(1L);
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
            signalCount++;
            instances.add(RowMapper.toInstance(inst));
        }

        AuditLogRow audit = new AuditLogRow();
        audit.setTenantId(cmd.tenantId());
        audit.setResourceType("DEFINITION");
        audit.setResourceId(String.valueOf(definitionId));
        audit.setDefinitionId(definitionId);
        audit.setTransitionId(transition.getTransitionId());
        audit.setEventType("CREATE");
        audit.setActorType(cmd.actorType());
        audit.setActorId(cmd.actorId());
        audit.setTraceId(transition.getTraceId());
        audit.setDetailJson("{\"signalCount\":" + signalCount + "}");
        audit.setCreatedAt(StorageTime.toUtcLdt(now));
        auditLogMapper.insert(audit);

        List<TriggerBindingRecord> bindings = List.of(RowMapper.toBinding(binding));
        return new CreatedDefinitionResult(
                RowMapper.toDefinition(definitionMapper.selectById(definitionId)),
                participants,
                bindings,
                instances,
                signalCount,
                false,
                null);
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
    }
}
