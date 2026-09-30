package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler.InAppNotificationHandler;
import cn.net.mxz.timeimprint.task.service.capability.notification.notification.port.NotificationMaterializationPort;
import cn.net.mxz.timeimprint.task.service.extension.materialization.context.ScenarioDataMaterializationContext;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializerKey;
import cn.net.mxz.timeimprint.task.service.extension.materialization.spi.ScenarioDataMaterializer;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.shared.model.PlannedSignalIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.storage.mysql.shared.mapper.RowMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.mapper.ActionJobMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.audit.mapper.AuditLogMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.action.row.ActionJobRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.audit.row.AuditLogRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row.TaskSignalRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Post-transition side effects: planned signals, action jobs, scenario materializers, audit.
 * Change reason: externalizable side effects independent of definition/instance CAS core.
 */
@Component
public class TransitionSideEffectWriter {

    private static final int MAX_SIGNAL_ATTEMPTS = 5;
    private static final int MAX_ACTION_ATTEMPTS = 5;

    private final TaskSignalMapper signalMapper;
    private final ActionJobMapper actionJobMapper;
    private final AuditLogMapper auditLogMapper;
    private final TaskInstanceMapper instanceMapper;
    private final NotificationMaterializationPort notificationPort;
    private final TransitionCommitSupport support;
    private final Map<ScenarioDataMaterializerKey, ScenarioDataMaterializer> materializers;

    public TransitionSideEffectWriter(
            TaskSignalMapper signalMapper,
            ActionJobMapper actionJobMapper,
            AuditLogMapper auditLogMapper,
            TaskInstanceMapper instanceMapper,
            NotificationMaterializationPort notificationPort,
            TransitionCommitSupport support,
            List<ScenarioDataMaterializer> materializerList) {
        this.signalMapper = signalMapper;
        this.actionJobMapper = actionJobMapper;
        this.auditLogMapper = auditLogMapper;
        this.instanceMapper = instanceMapper;
        this.notificationPort = notificationPort;
        this.support = support;
        this.materializers = materializerList.stream()
                .collect(Collectors.toMap(ScenarioDataMaterializer::registrationKey, m -> m));
    }

    int write(
            TransitionCommitRequest request,
            TransitionPlan plan,
            TransitionTarget target,
            TaskDefinitionRow defRow,
            String tenantId,
            long controlGeneration,
            Long transitionId,
            LocalDateTime now,
            int affectedRows) {
        // 6. Handle planned signals
        for (PlannedSignalIntent si : plan.plannedSignalIntents()) {
            TaskSignalRow existing = signalMapper.selectBySourceKey(tenantId, si.providerKey(), si.signalKey());
            if (existing != null) {
                continue; // Idempotent
            }
            String payloadJson = support.toJson(si.payload());
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
            row.setOccurredAt(support.toLocal(si.occurredAt()));
            row.setReceivedAt(now);
            row.setPayloadJson(payloadJson);
            row.setPayloadHash(Sha256.sha256Bytes(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            row.setProcessStatus("READY");
            row.setAttemptCount(0);
            row.setMaxAttempts(MAX_SIGNAL_ATTEMPTS);
            row.setNextAttemptAt(support.toLocal(si.occurredAt()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            signalMapper.insert(row);
            affectedRows++;
        }

        // 7. Handle action job intents + notifications
        for (ActionJobIntent aj : plan.actionJobIntents()) {
            String payloadJsonStr = support.toJson(aj.payload());
            Long notificationId = null;

            if (InAppNotificationHandler.HANDLER_KEY.equals(aj.handlerKey())
                    && transitionId != null && request.instanceId() != null) {
                // Extract notification content from payload
                Map<String, Object> fields = aj.payload() instanceof JsonPayload jp ? jp.fields() : Map.of();
                String title = support.toString(fields.get("title"), "");
                String body = support.toString(fields.get("body"), null);
                String purpose = support.toString(fields.get("purpose"), "INITIAL");

                notificationId = notificationPort.insertNotification(
                        tenantId, request.definitionId(), request.instanceId(), transitionId,
                        title, body, purpose, now);
                affectedRows++;

                // Re-build payload with notificationId injected
                Map<String, Object> enriched = new java.util.LinkedHashMap<>(fields);
                enriched.put("notificationId", notificationId);
                payloadJsonStr = support.toJsonFromMap(enriched);
            }

            byte[] payloadHash = Sha256.sha256Bytes(
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
            row.setAvailableAt(support.toLocal(aj.availableAt()));
            row.setExpiresAt(aj.expiresAt() != null ? support.toLocal(aj.expiresAt()) : null);
            row.setStatus("READY");
            row.setAttemptCount(0);
            row.setMaxAttempts(MAX_ACTION_ATTEMPTS);
            row.setNextAttemptAt(support.toLocal(aj.availableAt()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            actionJobMapper.insert(row);
            affectedRows++;
        }

        // 8. Call registered ScenarioDataMaterializers for any mutations in the plan
        if (!plan.scenarioDataMutations().isEmpty() && transitionId != null) {
            var defSnapshot = RowMapper.toDefinition(defRow);
            var instSnapshot = request.instanceId() != null
                    ? RowMapper.toInstance(instanceMapper.selectById(request.instanceId()))
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
                            new ScenarioDataMaterializationContext(defSnapshot, instSnapshot, transitionId, mutation));
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
            audit.setDetailJson("{\"summary\":" + support.jsonStringEscape(plan.auditSummary()) + "}");
            audit.setCreatedAt(now);
            auditLogMapper.insert(audit);
            affectedRows++;
        }


        return affectedRows;
    }
}
