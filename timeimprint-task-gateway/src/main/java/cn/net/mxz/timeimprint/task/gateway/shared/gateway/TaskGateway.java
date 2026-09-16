package cn.net.mxz.timeimprint.task.gateway.shared.gateway;

import cn.net.mxz.timeimprint.task.domain.definition.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.definition.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.signal.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.instance.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.shared.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.shared.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.shared.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.domain.shared.request.TriggerBindingInput;
import cn.net.mxz.timeimprint.task.domain.diagnostic.view.ActionJobDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandMetadataView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.inbox.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.shared.view.OccurrenceView;
import cn.net.mxz.timeimprint.task.domain.shared.view.Page;
import cn.net.mxz.timeimprint.task.domain.shared.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.shared.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.domain.signal.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.domain.signal.view.SignalDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.definition.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.domain.instance.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.diagnostic.view.TransitionDiagnosticView;
import cn.net.mxz.timeimprint.task.domain.shared.view.UnreadCountView;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.definition.service.CreateTaskDefinitionService;
import cn.net.mxz.timeimprint.task.service.application.definition.service.DefinitionCommandService;
import cn.net.mxz.timeimprint.task.service.application.inbox.service.InboxService;
import cn.net.mxz.timeimprint.task.service.application.instance.service.InstanceCommandService;
import cn.net.mxz.timeimprint.task.service.application.shared.service.ListQueryService;
import cn.net.mxz.timeimprint.task.service.application.shared.service.PreviewService;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RedriveService;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalIngressService;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
import cn.net.mxz.timeimprint.task.service.application.shared.service.TaskQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import cn.net.mxz.timeimprint.task.domain.shared.request.ParticipantInput;

@Component
public class TaskGateway {

    private final PreviewService previewService;
    private final CreateTaskDefinitionService createService;
    private final TaskQueryService queryService;
    private final ListQueryService listQueryService;
    private final RedriveService redriveService;
    private final InboxService inboxService;
    private final SignalIngressService signalIngressService;
    private final SignalProcessingService signalProcessingService;
    private final InstanceCommandService instanceCommandService;
    private final DefinitionCommandService definitionCommandService;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;
    private final TaskGatewayViews views;

    public TaskGateway(
            PreviewService previewService,
            CreateTaskDefinitionService createService,
            TaskQueryService queryService,
            ListQueryService listQueryService,
            RedriveService redriveService,
            InboxService inboxService,
            SignalIngressService signalIngressService,
            SignalProcessingService signalProcessingService,
            InstanceCommandService instanceCommandService,
            DefinitionCommandService definitionCommandService,
            BusinessClock clock,
            ObjectMapper objectMapper,
            TaskGatewayViews views) {
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
        this.views = views;
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
                .map(p -> new CreateDefinitionCommand.ParticipantInput(
                        p.principalType(), p.principalId(), p.roleCode()))
                .toList();
        var bindings = req.triggerBindings().stream()
                .map(b -> new CreateDefinitionCommand.TriggerBindingInput(
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
        return views.toDefinitionView(queryService.getDefinition(created.definition().definitionId()));
    }

    public TaskDefinitionView getDefinition(long definitionId) {
        return views.toDefinitionView(queryService.getDefinition(definitionId));
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
                .map(d -> views.toDefinitionView(queryService.getDefinition(d.definitionId())))
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
                .map(i -> views.toInstanceView(queryService.getInstance(i.instanceId())))
                .toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public SignalDiagnosticView getSignal(long signalId) {
        return views.toSignalDiag(listQueryService.getSignal(signalId));
    }

    public Page<ActionJobDiagnosticView> listActionJobs(
            Long definitionId, Long instanceId, String status, String handlerKey, String cursor, Integer limit) {
        var page = listQueryService.listActionJobs(
                definitionId, instanceId, status, handlerKey, cursor, limit == null ? 20 : limit);
        List<ActionJobDiagnosticView> items = page.items().stream().map(views::toActionDiag).toList();
        return new Page<>(items, page.nextCursor(), page.hasMore(), listQueryService.asOf().toString());
    }

    public ActionJobDiagnosticView getActionJob(long actionJobId) {
        return views.toActionDiag(listQueryService.getActionJob(actionJobId));
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
        return views.toSignalDiag(redriveService.redriveSignal(
                signalId, req.requestId(), req.expectedStatus(), req.reason()));
    }

    public ActionJobDiagnosticView redriveAction(long actionJobId, RedriveRequest req) {
        return views.toActionDiag(redriveService.redriveAction(
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
        var defView = views.toDefinitionView(queryService.getDefinition(definitionId));
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
        return views.toInstanceView(queryService.getInstance(instanceId));
    }

    public Page<InboxView> listInbox(boolean unreadOnly, int limit) {
        var items = inboxService.list(unreadOnly, limit).stream().map(views::toInbox).toList();
        return new Page<>(items, null, false, clock.nowUtcSeconds().toString());
    }

    public InboxView getInbox(long inboxId) {
        return views.toInbox(inboxService.get(inboxId));
    }

    public UnreadCountView unreadCount() {
        return new UnreadCountView(inboxService.unreadCount(), clock.nowUtcSeconds().toString());
    }

    public InboxView markRead(long inboxId, MarkReadRequest req) {
        return views.toInbox(inboxService.markRead(inboxId));
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

        var instView = views.toInstanceView(queryService.getInstance(instanceId));
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
}
