package cn.net.mxz.timeimprint.task.gateway;

import cn.net.mxz.timeimprint.task.domain.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.domain.request.TriggerBindingInput;
import cn.net.mxz.timeimprint.task.domain.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.AttemptSummary;
import cn.net.mxz.timeimprint.task.domain.view.CommandMetadataView;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.DeliverySummary;
import cn.net.mxz.timeimprint.task.domain.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.view.OccurrenceView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.domain.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.domain.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.domain.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.view.TransitionDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.view.TriggerBindingView;
import cn.net.mxz.timeimprint.task.domain.view.UnreadCountView;
import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzInboxRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzSignalRecord;
import cn.net.mxz.timeimprint.task.service.application.service.MxzCreateTaskDefinitionService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzDefinitionCommandService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzInboxService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzInstanceCommandService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzListQueryService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzPreviewService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzRedriveService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalIngressService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzTaskQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MxzTaskGateway {

    private final MxzPreviewService previewService;
    private final MxzCreateTaskDefinitionService createService;
    private final MxzTaskQueryService queryService;
    private final MxzListQueryService listQueryService;
    private final MxzRedriveService redriveService;
    private final MxzInboxService inboxService;
    private final MxzSignalIngressService signalIngressService;
    private final MxzSignalProcessingService signalProcessingService;
    private final MxzInstanceCommandService instanceCommandService;
    private final MxzDefinitionCommandService definitionCommandService;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzTaskGateway(
            MxzPreviewService previewService,
            MxzCreateTaskDefinitionService createService,
            MxzTaskQueryService queryService,
            MxzListQueryService listQueryService,
            MxzRedriveService redriveService,
            MxzInboxService inboxService,
            MxzSignalIngressService signalIngressService,
            MxzSignalProcessingService signalProcessingService,
            MxzInstanceCommandService instanceCommandService,
            MxzDefinitionCommandService definitionCommandService,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.previewService = previewService;
        this.createService = createService;
        this.queryService = queryService;
        this.listQueryService = listQueryService;
        this.redriveService = redriveService;
        this.inboxService = inboxService;
        this.signalIngressService = signalIngressService;
        this.signalProcessingService = signalProcessingService;
        this.instanceCommandService = instanceCommandService;
        this.definitionCommandService = definitionCommandService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public PreviewResult preview(PreviewRequest req) {
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (TriggerBindingInput b : req.triggerBindings()) {
            Map<String, Object> m = new HashMap<>();
            m.put("bindingKey", b.bindingKey());
            m.put("providerKey", b.providerKey());
            m.put("schemaVersion", b.schemaVersion());
            m.put("config", objectMapper.convertValue(b.config(), Map.class));
            bindings.add(m);
        }
        var out = previewService.preview(
                req.scenarioKey(),
                req.scenarioSchemaVersion(),
                req.scenarioConfig().toString(),
                bindings,
                Instant.parse(req.after()),
                req.limit());
        List<OccurrenceView> occs = out.occurrences().stream()
                .map(o -> new OccurrenceView(
                        o.occurrenceKey(),
                        o.occurrenceAt().toString(),
                        o.dueAt() == null ? null : o.dueAt().toString()))
                .toList();
        List<TriggerBindingInput> normalized = req.triggerBindings();
        return new PreviewResult(
                out.scenarioKey(),
                out.scenarioSchemaVersion(),
                normalized,
                req.scenarioConfig(),
                occs);
    }

    public TaskDefinitionView create(CreateTaskDefinitionRequest req) {
        var participants = req.participants().stream()
                .map(p -> new MxzCreateDefinitionCommand.ParticipantInput(
                        p.principalType(), p.principalId(), p.roleCode()))
                .toList();
        var bindings = req.triggerBindings().stream()
                .map(b -> new MxzCreateDefinitionCommand.TriggerBindingInput(
                        b.bindingKey(), b.providerKey(), b.schemaVersion(), b.config().toString()))
                .toList();
        var created = createService.create(
                req.requestId(),
                req.scenarioKey(),
                req.scenarioSchemaVersion(),
                req.title(),
                req.description(),
                req.scenarioConfig().toString(),
                participants,
                bindings);
        return toDefinitionView(queryService.getDefinition(created.definition().definitionId()));
    }

    public TaskDefinitionView getDefinition(long definitionId) {
        return toDefinitionView(queryService.getDefinition(definitionId));
    }

    public Page<ScenarioMetadataView> listScenarios(String cursor, Integer limit) {
        var page = listQueryService.listScenarios(cursor, limit == null ? 20 : limit);
        List<ScenarioMetadataView> items = page.items().stream()
                .map(s -> new ScenarioMetadataView(
                        s.scenarioKey(),
                        s.displayName(),
                        s.contractVersion(),
                        s.supportedScenarioSchemaVersions(),
                        s.definitionCommandKeys().stream()
                                .map(k -> new CommandMetadataView(k, List.of(1)))
                                .toList(),
                        s.instanceCommandKeys().stream()
                                .map(k -> new CommandMetadataView(k, List.of(1)))
                                .toList(),
                        s.requiredCapabilities()))
                .toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public Page<TaskDefinitionView> listDefinitions(
            String scenarioKey, String controlState, String participantRole, String cursor, Integer limit) {
        var page = listQueryService.listDefinitions(
                scenarioKey, controlState, participantRole, cursor, limit == null ? 20 : limit);
        List<TaskDefinitionView> items = page.items().stream()
                .map(d -> toDefinitionView(queryService.getDefinition(d.definitionId())))
                .toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public Page<TaskInstanceView> listInstances(
            Long definitionId,
            String scenarioKey,
            String lifecycleCategory,
            String scenarioState,
            String participantRole,
            String from,
            String to,
            String cursor,
            Integer limit) {
        Instant fromAt = from == null || from.isBlank() ? null : Instant.parse(from);
        Instant toAt = to == null || to.isBlank() ? null : Instant.parse(to);
        var page = listQueryService.listInstances(
                definitionId,
                scenarioKey,
                lifecycleCategory,
                scenarioState,
                participantRole,
                fromAt,
                toAt,
                cursor,
                limit == null ? 20 : limit);
        List<TaskInstanceView> items = page.items().stream()
                .map(i -> toInstanceView(queryService.getInstance(i.instanceId())))
                .toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public SignalDiagnosticView getSignal(long signalId) {
        return toSignalDiag(listQueryService.getSignal(signalId));
    }

    public Page<ActionJobDiagnosticView> listActionJobs(
            Long definitionId, Long instanceId, String status, String handlerKey, String cursor, Integer limit) {
        var page = listQueryService.listActionJobs(
                definitionId, instanceId, status, handlerKey, cursor, limit == null ? 20 : limit);
        List<ActionJobDiagnosticView> items = page.items().stream().map(this::toActionDiag).toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public ActionJobDiagnosticView getActionJob(long actionJobId) {
        return toActionDiag(listQueryService.getActionJob(actionJobId));
    }

    public Page<TransitionDiagnosticView> listTransitions(
            Long definitionId, Long instanceId, String cursor, Integer limit) {
        var page = listQueryService.listTransitions(definitionId, instanceId, cursor, limit == null ? 20 : limit);
        List<TransitionDiagnosticView> items = page.items().stream()
                .map(t -> new TransitionDiagnosticView(
                        String.valueOf(t.transitionId()),
                        String.valueOf(t.definitionId()),
                        t.instanceId() == null ? null : String.valueOf(t.instanceId()),
                        t.sourceType(),
                        t.sourceKey(),
                        t.commandKey(),
                        t.fromControlState(),
                        t.toControlState(),
                        t.fromLifecycle(),
                        t.toLifecycle(),
                        t.fromScenarioState(),
                        t.toScenarioState(),
                        t.fromRevision(),
                        t.toRevision(),
                        t.actorType(),
                        t.actorId(),
                        t.traceId(),
                        t.createdAt() == null ? null : t.createdAt().toString()))
                .toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public SignalDiagnosticView redriveSignal(long signalId, RedriveRequest req) {
        return toSignalDiag(redriveService.redriveSignal(
                signalId, req.requestId(), req.expectedStatus(), req.reason()));
    }

    public ActionJobDiagnosticView redriveAction(long actionJobId, RedriveRequest req) {
        return toActionDiag(redriveService.redriveAction(
                actionJobId, req.requestId(), req.expectedStatus(), req.reason()));
    }

    /** E06: update / pause / resume / retire a definition. */
    public CommandResultView executeDefinitionCommand(long definitionId, String commandKey, DefinitionCommandRequest req) {
        var result = definitionCommandService.execute(
                definitionId,
                commandKey,
                req.requestId(),
                req.expectedRevision(),
                req.commandSchemaVersion(),
                req.payload());
        var defView = toDefinitionView(queryService.getDefinition(definitionId));
        JsonNode defNode = objectMapper.valueToTree(defView);
        return new CommandResultView(
                "DEFINITION",
                String.valueOf(definitionId),
                result.revision(),
                result.changed(),
                defNode,
                objectMapper.createObjectNode());
    }

    public TaskInstanceView getInstance(long instanceId) {
        return toInstanceView(queryService.getInstance(instanceId));
    }

    public Page<InboxView> listInbox(boolean unreadOnly, int limit) {
        var items = inboxService.list(unreadOnly, limit).stream().map(this::toInbox).toList();
        return new Page<>(items, null, false, clock.nowUtcSeconds().toString());
    }

    public InboxView getInbox(long inboxId) {
        return toInbox(inboxService.get(inboxId));
    }

    public UnreadCountView unreadCount() {
        return new UnreadCountView(inboxService.unreadCount(), clock.nowUtcSeconds().toString());
    }

    public InboxView markRead(long inboxId, MarkReadRequest req) {
        return toInbox(inboxService.markRead(inboxId));
    }

    public SignalAcceptedView acceptSignal(String providerKey, InternalSignalRequest req) {
        long definitionId = Long.parseLong(req.subject().definitionId());
        Long instanceId = req.subject().instanceId() == null || req.subject().instanceId().isBlank()
                ? null
                : Long.parseLong(req.subject().instanceId());
        var accepted = signalIngressService.accept(
                req.requestId(),
                providerKey,
                req.signalKey(),
                req.schemaVersion(),
                Instant.parse(req.occurredAt()),
                definitionId,
                instanceId,
                req.payload().toString());
        return new SignalAcceptedView(
                String.valueOf(accepted.signalId()),
                accepted.duplicated(),
                accepted.processStatus(),
                accepted.receivedAt().toString());
    }

    /** E09: execute an instance-level scenario command (complete, skip, snooze…). */
    public CommandResultView executeInstanceCommand(long instanceId, String commandKey, InstanceCommandRequest req) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(req.payload());
        } catch (Exception e) {
            payloadJson = "{}";
        }
        var result = instanceCommandService.execute(
                instanceId,
                commandKey,
                req.commandSchemaVersion(),
                req.requestId(),
                req.expectedRevision(),
                payloadJson);

        var instView = toInstanceView(queryService.getInstance(instanceId));
        JsonNode instNode = objectMapper.valueToTree(instView);
        JsonNode scenarioResultNode = objectMapper.convertValue(
                result.scenarioResult() == null ? Map.of() : result.scenarioResult(), JsonNode.class);

        return new CommandResultView(
                "INSTANCE",
                String.valueOf(instanceId),
                result.newRevision(),
                result.changed(),
                instNode,
                scenarioResultNode);
    }

    /** T02 helper: process a persisted READY signal (worker substitute). */
    public void processSignal(long signalId) {
        signalProcessingService.processSignal(signalId);
    }

    private TaskDefinitionView toDefinitionView(MxzTaskQueryService.DefinitionDetail d) {
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

    private TaskInstanceView toInstanceView(MxzTaskQueryService.InstanceDetail d) {
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

    private InboxView toInbox(MxzInboxRecord r) {
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

    private SignalDiagnosticView toSignalDiag(MxzSignalRecord s) {
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

    private ActionJobDiagnosticView toActionDiag(MxzActionJobRecord a) {
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
