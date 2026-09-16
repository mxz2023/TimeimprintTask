package cn.net.mxz.timeimprint.task.gateway.shared.gateway;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.domain.diagnostic.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.shared.view.AttemptSummary;
import cn.net.mxz.timeimprint.task.domain.shared.view.DeliverySummary;
import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.shared.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.signal.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.definition.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.domain.instance.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.shared.view.TriggerBindingView;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.signal.model.SignalRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.service.ListQueryService;
import cn.net.mxz.timeimprint.task.service.application.shared.service.TaskQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Gateway read-model mapping separated from {@link TaskGateway} command/query orchestration.
 * Change reason: HTTP view projection / deliveryState derivation (A42).
 */
@Component
public class TaskGatewayViews {

    private final TaskQueryService queryService;
    private final ListQueryService listQueryService;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public TaskGatewayViews(
            TaskQueryService queryService,
            ListQueryService listQueryService,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.listQueryService = listQueryService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    TaskDefinitionView toDefinitionView(TaskQueryService.DefinitionDetail d) {
        var def = d.definition();
        JsonNode cfg;
        try {
            cfg = objectMapper.readTree(def.scenarioConfigJson());
        } catch (Exception e) {
            cfg = objectMapper.createObjectNode();
        }
        List<ParticipantView> parts = d.participants().stream()
                .map(p -> new ParticipantView(p.principalType(), p.principalId(), p.roleCode(), p.sourceCode()))
                .toList();
        List<TriggerBindingView> binds = d.bindings().stream()
                .map(b -> {
                    JsonNode conf;
                    try {
                        conf = objectMapper.readTree(b.configJson());
                    } catch (Exception e) {
                        conf = objectMapper.createObjectNode();
                    }
                    return new TriggerBindingView(
                            String.valueOf(b.triggerBindingId()),
                            b.bindingKey(),
                            b.providerKey(),
                            b.schemaVersion(),
                            conf,
                            b.bindingState(),
                            b.scheduleGeneration(),
                            b.nextFireAt() == null ? null : b.nextFireAt().toString(),
                            b.exhausted(),
                            b.revision());
                })
                .toList();
        return new TaskDefinitionView(
                String.valueOf(def.definitionId()),
                def.scenarioKey(),
                def.scenarioSchemaVersion(),
                def.title(),
                def.description(),
                def.controlState().name(),
                def.controlGeneration(),
                def.revision(),
                parts,
                binds,
                cfg,
                d.allowedCommands(),
                def.createdAt().toString(),
                def.updatedAt().toString(),
                null,
                null);
    }

    TaskInstanceView toInstanceView(TaskQueryService.InstanceDetail d) {
        var inst = d.instance();
        var def = d.definition();
        List<ParticipantView> parts = d.participants().stream()
                .map(p -> new ParticipantView(p.principalType(), p.principalId(), p.roleCode(), p.sourceCode()))
                .toList();
        int ready = 0,
                running = 0,
                retryWait = 0,
                succeeded = 0,
                dead = 0,
                cancelled = 0,
                expired = 0,
                unknown = 0;
        long currentGen = def.controlGeneration();
        for (var a : d.actions()) {
            String status = a.status();
            boolean genMismatch = a.definitionControlGeneration() != currentGen;
            if (genMismatch && ("READY".equals(status) || "RETRY_WAIT".equals(status) || "RUNNING".equals(status))) {
                // Control barrier: uncleaned stale work counts as CANCELLED (04 / A42).
                // EXTERNAL with effectStartedAt remains RUNNING until closed — approximate via status only.
                if ("RUNNING".equals(status) && "EXTERNAL".equals(a.executionMode())) {
                    running++;
                } else {
                    cancelled++;
                }
                continue;
            }
            switch (status) {
                case "READY" -> ready++;
                case "RUNNING" -> running++;
                case "RETRY_WAIT" -> retryWait++;
                case "SUCCEEDED" -> succeeded++;
                case "DEAD" -> dead++;
                case "CANCELLED" -> cancelled++;
                case "EXPIRED" -> expired++;
                case "UNKNOWN" -> unknown++;
                default -> {
                }
            }
        }
        int total = d.actions().size();
        String deliveryState = deriveDeliveryState(
                total, ready, running, retryWait, succeeded, dead, cancelled, expired, unknown);
        DeliverySummary delivery = new DeliverySummary(
                deliveryState,
                total,
                ready,
                running,
                retryWait,
                succeeded,
                dead,
                cancelled,
                expired,
                unknown,
                d.inboxCount(),
                d.unreadInboxCount(),
                clock.nowUtcSeconds().toString());
        JsonNode projection = objectMapper.createObjectNode();
        try {
            projection = objectMapper.readTree(inst.scenarioSnapshotJson());
        } catch (Exception ignored) {
        }
        return new TaskInstanceView(
                String.valueOf(inst.instanceId()),
                String.valueOf(inst.definitionId()),
                def.scenarioKey(),
                inst.scenarioSchemaVersion(),
                inst.lifecycleCategory().name(),
                inst.scenarioState(),
                inst.revision(),
                inst.titleSnapshot(),
                inst.descriptionSnapshot(),
                parts,
                inst.occurrenceAt() == null ? null : inst.occurrenceAt().toString(),
                inst.dueAt() == null ? null : inst.dueAt().toString(),
                List.of(),
                projection,
                delivery,
                null,
                null,
                inst.terminalAt() == null ? null : inst.terminalAt().toString());
    }

    InboxView toInbox(InboxRecord r) {
        return new InboxView(
                String.valueOf(r.inboxId()),
                String.valueOf(r.notificationId()),
                String.valueOf(r.actionJobId()),
                String.valueOf(r.definitionId()),
                String.valueOf(r.instanceId()),
                r.scenarioKey(),
                r.purpose(),
                r.title(),
                r.body(),
                r.readAt() == null ? null : r.readAt().toString(),
                r.createdAt().toString());
    }

    SignalDiagnosticView toSignalDiag(SignalRecord s) {
        return new SignalDiagnosticView(
                String.valueOf(s.signalId()),
                String.valueOf(s.definitionId()),
                s.instanceId() == null ? null : String.valueOf(s.instanceId()),
                s.providerKey(),
                s.signalKey(),
                s.schemaVersion(),
                s.processStatus(),
                s.attemptCount(),
                s.maxAttempts(),
                s.nextAttemptAt() == null ? null : s.nextAttemptAt().toString(),
                s.leaseOwner(),
                s.leaseUntil() == null ? null : s.leaseUntil().toString(),
                s.resultCode(),
                s.resultSummary(),
                s.occurredAt() == null ? null : s.occurredAt().toString(),
                s.receivedAt() == null ? null : s.receivedAt().toString(),
                s.processedAt() == null ? null : s.processedAt().toString(),
                s.parentSignalId() == null ? null : String.valueOf(s.parentSignalId()),
                s.redriveNo());
    }

    ActionJobDiagnosticView toActionDiag(ActionJobRecord a) {
        var def = queryService.getDefinition(a.definitionId()).definition();
        String stored = a.status();
        String effective = stored;
        if (def.controlGeneration() != a.definitionControlGeneration()
                && ("READY".equals(stored) || "RETRY_WAIT".equals(stored) || "RUNNING".equals(stored))) {
            effective = "CANCELLED";
        }
        List<AttemptSummary> attempts = listQueryService.listAttempts(a.actionJobId()).stream()
                .map(t -> new AttemptSummary(
                        t.attemptNo(),
                        t.startedAt() == null ? null : t.startedAt().toString(),
                        t.finishedAt() == null ? null : t.finishedAt().toString(),
                        t.effectStartedAt() != null,
                        t.outcome(),
                        t.errorClass(),
                        t.errorCode(),
                        t.providerReference(),
                        t.safeSummary()))
                .toList();
        return new ActionJobDiagnosticView(
                String.valueOf(a.actionJobId()),
                String.valueOf(a.definitionId()),
                String.valueOf(a.instanceId()),
                String.valueOf(a.transitionId()),
                a.handlerKey(),
                a.executionMode(),
                a.schemaVersion(),
                a.targetType(),
                stored,
                effective,
                a.attemptCount(),
                a.maxAttempts(),
                a.availableAt() == null ? null : a.availableAt().toString(),
                a.expiresAt() == null ? null : a.expiresAt().toString(),
                a.nextAttemptAt() == null ? null : a.nextAttemptAt().toString(),
                a.leaseOwner(),
                a.leaseUntil() == null ? null : a.leaseUntil().toString(),
                a.outcomeCode(),
                a.outcomeSummary(),
                a.completedAt() == null ? null : a.completedAt().toString(),
                a.parentActionJobId() == null ? null : String.valueOf(a.parentActionJobId()),
                a.redriveNo(),
                attempts);
    }

    /** 04 DeliverySummary.deliveryState priority (A42). */
    static String deriveDeliveryState(
            int total,
            int ready,
            int running,
            int retryWait,
            int succeeded,
            int dead,
            int cancelled,
            int expired,
            int unknown) {
        if (total == 0) {
            return "NOT_SCHEDULED";
        }
        if (unknown > 0) {
            return "UNKNOWN";
        }
        if (ready + running + retryWait > 0) {
            return "IN_PROGRESS";
        }
        if (succeeded == total) {
            return "DELIVERED";
        }
        if (succeeded > 0 && (dead + cancelled + expired) > 0) {
            return "PARTIALLY_DELIVERED";
        }
        if (succeeded == 0 && dead > 0) {
            return "FAILED";
        }
        if (expired > 0) {
            return "EXPIRED";
        }
        return "CANCELLED";
    }
}
