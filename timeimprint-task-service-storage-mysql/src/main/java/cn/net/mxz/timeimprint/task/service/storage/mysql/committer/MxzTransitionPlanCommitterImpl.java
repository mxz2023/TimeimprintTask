package cn.net.mxz.timeimprint.task.service.storage.mysql.committer;

import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.limit.MxzPlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.limit.MxzTransitionPlanWriteValidator;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitResult;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.capability.notification.handler.MxzInAppNotificationHandler;
import cn.net.mxz.timeimprint.task.service.capability.notification.port.NotificationMaterializationPort;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ParticipantChange;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ParticipantChangeKind;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.PlannedSignalIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TriggerBindingChange;
import cn.net.mxz.timeimprint.task.service.storage.mysql.MxzRowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.ActionJobMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.AuditLogMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskParticipantMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.ActionJobRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.AuditLogRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskTransitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TriggerBindingRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Atomically commits a TransitionPlan following the parent-first lock order.
 * Caller must already hold all relevant locks.
 */
@Component
public class MxzTransitionPlanCommitterImpl implements TransitionPlanCommitter {

    private static final int MAX_SIGNAL_ATTEMPTS = 5;
    private static final int MAX_ACTION_ATTEMPTS = 5;

    private final TaskDefinitionMapper definitionMapper;
    private final TriggerBindingMapper triggerMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskSignalMapper signalMapper;
    private final TaskTransitionMapper transitionMapper;
    private final ActionJobMapper actionJobMapper;
    private final AuditLogMapper auditLogMapper;
    private final NotificationMaterializationPort notificationPort;
    private final ObjectMapper objectMapper;
    /** Registered scenario data materializers, keyed by (scenarioKey, mutationKey, schemaVersion). */
    private final Map<ScenarioDataMaterializerKey, ScenarioDataMaterializer> materializers;

    public MxzTransitionPlanCommitterImpl(
            TaskDefinitionMapper definitionMapper,
            TriggerBindingMapper triggerMapper,
            TaskInstanceMapper instanceMapper,
            TaskParticipantMapper participantMapper,
            TaskSignalMapper signalMapper,
            TaskTransitionMapper transitionMapper,
            ActionJobMapper actionJobMapper,
            AuditLogMapper auditLogMapper,
            NotificationMaterializationPort notificationPort,
            ObjectMapper objectMapper,
            List<ScenarioDataMaterializer> materializerList) {
        this.definitionMapper = definitionMapper;
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.participantMapper = participantMapper;
        this.signalMapper = signalMapper;
        this.transitionMapper = transitionMapper;
        this.actionJobMapper = actionJobMapper;
        this.auditLogMapper = auditLogMapper;
        this.notificationPort = notificationPort;
        this.objectMapper = objectMapper;
        this.materializers = materializerList.stream()
                .collect(Collectors.toMap(ScenarioDataMaterializer::registrationKey, m -> m));
    }

    @Override
    public MxzTransitionCommitResult commit(MxzTransitionCommitRequest request) {
        TransitionPlan plan = request.plan();
        MxzTransitionPlanWriteValidator.validateBeforeWrite(plan, objectMapper);
        TransitionTarget target = plan.target();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int affectedRows = 0;

        // 1. Read definition (already locked by caller)
        TaskDefinitionRow defRow = definitionMapper.selectById(request.definitionId());
        if (defRow == null) {
            throw new IllegalStateException("Definition not found: " + request.definitionId());
        }
        String tenantId = defRow.getTenantId();
        long controlGeneration = defRow.getControlGeneration();

        // 2. Handle definition control transition
        DefinitionControlTransition defTransition = plan.definitionControlTransition();
        if (defTransition != null) {
            String newControlState = defTransition.toControlState().name();
            long newControlGen = controlGeneration;
            LocalDateTime pausedAt = defRow.getPausedAt();
            LocalDateTime retiredAt = defRow.getRetiredAt();
            if ("PAUSED".equals(newControlState) && !"PAUSED".equals(defRow.getControlState())) {
                pausedAt = now;
                newControlGen = controlGeneration + 1;
            } else if ("RETIRED".equals(newControlState)) {
                retiredAt = now;
                newControlGen = controlGeneration + 1;
            } else if ("ACTIVE".equals(newControlState) && "PAUSED".equals(defRow.getControlState())) {
                newControlGen = controlGeneration + 1;
                pausedAt = null;
            }
            int updated = definitionMapper.updateRevision(
                    request.definitionId(),
                    defRow.getRevision(),
                    defRow.getRevision() + 1,
                    newControlState,
                    newControlGen,
                    "SYSTEM",
                    now,
                    pausedAt,
                    retiredAt);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for definition " + request.definitionId());
            }
            affectedRows += updated;
            controlGeneration = newControlGen;
        }

        // 3. Handle trigger binding changes
        for (TriggerBindingChange tbc : plan.triggerBindingChanges()) {
            List<TriggerBindingRow> bindings = triggerMapper.selectByDefinitionId(request.definitionId());
            TriggerBindingRow binding = bindings.stream()
                    .filter(b -> b.getBindingKey().equals(tbc.bindingKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Binding not found: " + tbc.bindingKey()));
            LocalDateTime nextFireAt = tbc.nextFireAtEpochSecond() != null
                    ? LocalDateTime.ofEpochSecond(tbc.nextFireAtEpochSecond(), 0, ZoneOffset.UTC)
                    : null;
            boolean exhausted = Boolean.TRUE.equals(tbc.exhausted());
            String cursorJson = toJson(tbc.configPayload());
            int updated = triggerMapper.updateCursor(
                    binding.getTriggerBindingId(),
                    binding.getRevision(),
                    binding.getRevision() + 1,
                    nextFireAt,
                    cursorJson,
                    exhausted,
                    now);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for binding " + binding.getTriggerBindingId());
            }
            affectedRows += updated;
        }

        // 4. Handle participant changes
        for (ParticipantChange pc : plan.participantChanges()) {
            if (pc.changeKind() == ParticipantChangeKind.ADD) {
                TaskParticipantRow row = new TaskParticipantRow();
                row.setDefinitionId(request.definitionId());
                row.setInstanceId(pc.definitionLevel() ? null : request.instanceId());
                row.setPrincipalType(pc.principalType());
                row.setPrincipalId(pc.principalId());
                row.setRoleCode(pc.roleCode());
                row.setSourceCode("DIRECT");
                row.setMetadataJson("{}");
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                participantMapper.insert(row);
                affectedRows++;
            }
        }

        // 5. Build and insert transition record
        Long transitionId = null;
        InstanceStateTransition instTransition = plan.instanceStateTransition();

        if (instTransition != null && request.instanceId() != null) {
            TaskInstanceRow instRow = instanceMapper.selectById(request.instanceId());
            if (instRow == null) {
                throw new IllegalStateException("Instance not found: " + request.instanceId());
            }

            TaskTransitionRow tr = buildInstanceTransitionRow(request, defRow, instRow, now);
            transitionMapper.insert(tr);
            transitionId = tr.getTransitionId();
            affectedRows++;

            // Update instance state
            String newLifecycle = instTransition.toLifecycleCategory().name();
            String newState = instTransition.toScenarioState();
            LocalDateTime terminalAt = "TERMINAL".equals(newLifecycle) ? now : null;

            String snapshotJson = instRow.getScenarioSnapshotJson();
            byte[] snapshotHash = instRow.getSnapshotHash();
            if (request.instanceSnapshotJson() != null && !request.instanceSnapshotJson().isBlank()) {
                snapshotJson = request.instanceSnapshotJson();
                snapshotHash = MxzSha256.digestUtf8(snapshotJson);
            } else {
                for (ScenarioDataMutation mutation : plan.scenarioDataMutations()) {
                    if ("REPLACE_INSTANCE_SNAPSHOT".equals(mutation.mutationKey())
                            && mutation.payload() instanceof MxzJsonPayload jp) {
                        snapshotJson = toJson(jp);
                        snapshotHash = MxzSha256.digestUtf8(snapshotJson);
                        break;
                    }
                }
            }

            int updated = instanceMapper.updateRevision(
                    request.instanceId(),
                    instRow.getRevision(),
                    instRow.getRevision() + 1,
                    newLifecycle,
                    newState,
                    snapshotJson,
                    snapshotHash,
                    terminalAt,
                    now);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for instance " + request.instanceId());
            }
            affectedRows += updated;

        } else if (defTransition != null || !plan.plannedSignalIntents().isEmpty()
                || !plan.actionJobIntents().isEmpty()
                || !plan.scenarioDataMutations().isEmpty()) {
            // Definition-level transition (creation, control command, or scenario mutation)
            TaskTransitionRow tr = buildDefinitionTransitionRow(request, defRow, now);
            transitionMapper.insert(tr);
            transitionId = tr.getTransitionId();
            affectedRows++;
        }

        // 6. Handle planned signals
        for (PlannedSignalIntent si : plan.plannedSignalIntents()) {
            TaskSignalRow existing = signalMapper.selectBySourceKey(tenantId, si.providerKey(), si.signalKey());
            if (existing != null) {
                continue; // Idempotent
            }
            String payloadJson = toJson(si.payload());
            TaskSignalRow row = new TaskSignalRow();
            row.setTenantId(tenantId);
            row.setDefinitionId(si.definitionId());
            row.setTriggerBindingId(null); // planner sets this
            row.setInstanceId(si.instanceId());
            row.setDefinitionControlGeneration(controlGeneration);
            row.setParentSignalId(null);
            row.setRedriveNo(0);
            row.setProviderKey(si.providerKey());
            row.setSignalKey(si.signalKey());
            row.setSchemaVersion(si.schemaVersion());
            row.setOccurredAt(toLocal(si.occurredAt()));
            row.setReceivedAt(now);
            row.setPayloadJson(payloadJson);
            row.setPayloadHash(MxzSha256.sha256Bytes(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            row.setProcessStatus("READY");
            row.setAttemptCount(0);
            row.setMaxAttempts(MAX_SIGNAL_ATTEMPTS);
            row.setNextAttemptAt(toLocal(si.occurredAt()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            signalMapper.insert(row);
            affectedRows++;
        }

        // 7. Handle action job intents + notifications
        for (ActionJobIntent aj : plan.actionJobIntents()) {
            String payloadJsonStr = toJson(aj.payload());
            Long notificationId = null;

            if (MxzInAppNotificationHandler.HANDLER_KEY.equals(aj.handlerKey())
                    && transitionId != null && request.instanceId() != null) {
                // Extract notification content from payload
                Map<String, Object> fields = aj.payload() instanceof MxzJsonPayload jp ? jp.fields() : Map.of();
                String title = toString(fields.get("title"), "");
                String body = toString(fields.get("body"), null);
                String purpose = toString(fields.get("purpose"), "INITIAL");

                notificationId = notificationPort.insertNotification(
                        tenantId, request.definitionId(), request.instanceId(), transitionId,
                        title, body, purpose, now);
                affectedRows++;

                // Re-build payload with notificationId injected
                Map<String, Object> enriched = new java.util.LinkedHashMap<>(fields);
                enriched.put("notificationId", notificationId);
                payloadJsonStr = toJsonFromMap(enriched);
            }

            byte[] payloadHash = MxzSha256.sha256Bytes(
                    payloadJsonStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ActionJobRow row = new ActionJobRow();
            row.setTenantId(tenantId);
            row.setDefinitionId(request.definitionId());
            row.setInstanceId(request.instanceId() != null ? request.instanceId() : 0L);
            row.setTransitionId(transitionId != null ? transitionId : 0L);
            row.setDefinitionControlGeneration(controlGeneration);
            row.setParentActionJobId(null);
            row.setRedriveNo(0);
            row.setHandlerKey(aj.handlerKey());
            row.setActionKey(aj.actionKey());
            row.setExecutionMode(aj.executionMode());
            row.setSchemaVersion(aj.actionSchemaVersion());
            row.setTargetType(aj.targetType());
            row.setTargetId(aj.targetId());
            row.setPayloadJson(payloadJsonStr);
            row.setPayloadHash(payloadHash);
            row.setAvailableAt(toLocal(aj.availableAt()));
            row.setExpiresAt(aj.expiresAt() != null ? toLocal(aj.expiresAt()) : null);
            row.setStatus("READY");
            row.setAttemptCount(0);
            row.setMaxAttempts(MAX_ACTION_ATTEMPTS);
            row.setNextAttemptAt(toLocal(aj.availableAt()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            actionJobMapper.insert(row);
            affectedRows++;
        }

        // 8. Call registered ScenarioDataMaterializers for any mutations in the plan
        if (!plan.scenarioDataMutations().isEmpty() && transitionId != null) {
            var defSnapshot = MxzRowMapper.toDefinition(defRow);
            var instSnapshot = request.instanceId() != null
                    ? MxzRowMapper.toInstance(instanceMapper.selectById(request.instanceId()))
                    : null;
            for (ScenarioDataMutation mutation : plan.scenarioDataMutations()) {
                if ("REPLACE_INSTANCE_SNAPSHOT".equals(mutation.mutationKey())) {
                    continue; // handled during instance update
                }
                var key = new ScenarioDataMaterializerKey(
                        mutation.scenarioKey(), mutation.mutationKey(), mutation.schemaVersion());
                var materializer = materializers.get(key);
                if (materializer != null) {
                    affectedRows += materializer.materialize(
                            new MxzScenarioDataMaterializationContext(defSnapshot, instSnapshot, transitionId, mutation));
                }
            }
        }

        // 9. Write audit log
        if (!plan.auditSummary().isBlank() && transitionId != null) {
            AuditLogRow audit = new AuditLogRow();
            audit.setTenantId(tenantId);
            audit.setResourceType(target.resourceType() == TransitionResourceType.INSTANCE ? "INSTANCE" : "DEFINITION");
            audit.setResourceId(String.valueOf(target.resourceId()));
            audit.setDefinitionId(request.definitionId());
            audit.setInstanceId(request.instanceId());
            audit.setTransitionId(transitionId);
            audit.setEventType("TRANSITION");
            audit.setActorType("SYSTEM");
            audit.setActorId("system");
            audit.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
            audit.setDetailJson("{\"summary\":" + jsonStringEscape(plan.auditSummary()) + "}");
            audit.setCreatedAt(now);
            auditLogMapper.insert(audit);
            affectedRows++;
        }

        long toRevision;
        if (target.resourceType() == TransitionResourceType.INSTANCE && request.instanceId() != null) {
            TaskInstanceRow r = instanceMapper.selectById(request.instanceId());
            toRevision = r != null ? r.getRevision() : 1L;
        } else {
            TaskDefinitionRow r = definitionMapper.selectById(request.definitionId());
            toRevision = r != null ? r.getRevision() : 1L;
        }

        if (affectedRows > MxzPlatformLimits.MAX_MUTATED_ROWS_PER_WRITE_TX) {
            throw new MxzApplicationException("INVALID_REQUEST", "mutated row count exceeded limit");
        }

        return new MxzTransitionCommitResult(
                transitionId != null ? transitionId : 0L,
                toRevision,
                affectedRows,
                true);
    }

    private TaskTransitionRow buildInstanceTransitionRow(MxzTransitionCommitRequest request,
            TaskDefinitionRow defRow, TaskInstanceRow instRow, LocalDateTime now) {
        InstanceStateTransition ist = request.plan().instanceStateTransition();
        TaskTransitionRow tr = new TaskTransitionRow();
        tr.setDefinitionId(request.definitionId());
        tr.setInstanceId(request.instanceId());
        tr.setSourceType(request.sourceType());
        tr.setSourceKey(request.sourceKey());
        tr.setCommandKey("COMMAND".equals(request.sourceType()) ? request.sourceKey() : null);
        tr.setFromControlState(null);
        tr.setToControlState(null);
        tr.setFromLifecycle(ist != null ? ist.fromLifecycleCategory().name() : instRow.getLifecycleCategory());
        tr.setToLifecycle(ist != null ? ist.toLifecycleCategory().name() : instRow.getLifecycleCategory());
        tr.setFromScenarioState(ist != null ? ist.fromScenarioState() : instRow.getScenarioState());
        tr.setToScenarioState(ist != null ? ist.toScenarioState() : instRow.getScenarioState());
        tr.setFromRevision(instRow.getRevision());
        tr.setToRevision(instRow.getRevision() + 1);
        tr.setActorType("SYSTEM");
        tr.setActorId("system");
        tr.setSummaryJson("{\"source\":\"" + request.sourceKey() + "\"}");
        tr.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        tr.setCreatedAt(now);
        return tr;
    }

    private TaskTransitionRow buildDefinitionTransitionRow(MxzTransitionCommitRequest request,
            TaskDefinitionRow defRow, LocalDateTime now) {
        DefinitionControlTransition dct = request.plan().definitionControlTransition();
        String fromState = dct != null ? dct.fromControlState().name() : defRow.getControlState();
        String toState = dct != null ? dct.toControlState().name() : defRow.getControlState();
        TaskTransitionRow tr = new TaskTransitionRow();
        tr.setDefinitionId(request.definitionId());
        tr.setInstanceId(null);
        tr.setSourceType(request.sourceType());
        tr.setSourceKey(request.sourceKey());
        tr.setCommandKey("COMMAND".equals(request.sourceType()) ? request.sourceKey() : null);
        tr.setFromControlState(fromState);
        tr.setToControlState(toState);
        tr.setFromLifecycle(null);
        tr.setToLifecycle(null);
        tr.setFromScenarioState(null);
        tr.setToScenarioState(null);
        tr.setFromRevision(defRow.getRevision());
        tr.setToRevision(defRow.getRevision() + 1);
        tr.setActorType("SYSTEM");
        tr.setActorId("system");
        tr.setSummaryJson("{\"source\":\"" + request.sourceKey() + "\"}");
        tr.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        tr.setCreatedAt(now);
        return tr;
    }

    private String toJson(ScenarioMutationPayload payload) {
        if (payload == null) return "{}";
        if (payload instanceof MxzJsonPayload jp) {
            return toJsonFromMap(jp.fields());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toJsonFromMap(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private LocalDateTime toLocal(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String toString(Object o, String defaultVal) {
        return o != null ? String.valueOf(o) : defaultVal;
    }

    private String jsonStringEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
