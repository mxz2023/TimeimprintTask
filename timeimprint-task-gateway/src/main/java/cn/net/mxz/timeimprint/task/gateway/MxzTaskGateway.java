package cn.net.mxz.timeimprint.task.gateway;

import cn.net.mxz.timeimprint.task.domain.request.CreateTaskDefinitionRequest;
import cn.net.mxz.timeimprint.task.domain.request.DefinitionCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.InternalSignalRequest;
import cn.net.mxz.timeimprint.task.domain.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.request.MarkReadRequest;
import cn.net.mxz.timeimprint.task.domain.request.PreviewRequest;
import cn.net.mxz.timeimprint.task.domain.request.TriggerBindingInput;
import cn.net.mxz.timeimprint.task.domain.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.view.DeliverySummary;
import cn.net.mxz.timeimprint.task.domain.view.InboxView;
import cn.net.mxz.timeimprint.task.domain.view.OccurrenceView;
import cn.net.mxz.timeimprint.task.domain.view.Page;
import cn.net.mxz.timeimprint.task.domain.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.view.PreviewResult;
import cn.net.mxz.timeimprint.task.domain.view.SignalAcceptedView;
import cn.net.mxz.timeimprint.task.domain.view.TaskDefinitionView;
import cn.net.mxz.timeimprint.task.domain.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.view.TriggerBindingView;
import cn.net.mxz.timeimprint.task.domain.view.UnreadCountView;
import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzInboxRecord;
import cn.net.mxz.timeimprint.task.service.application.service.MxzCreateTaskDefinitionService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzDefinitionCommandService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzInboxService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzInstanceCommandService;
import cn.net.mxz.timeimprint.task.service.application.service.MxzPreviewService;
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
                .map(o -> new OccurrenceView(o.occurrenceKey(), o.occurrenceAt().toString(), o.dueAt().toString()))
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
        try {
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
        } catch (MxzApplicationException ex) {
            if ("IDEMPOTENCY_REPLAY".equals(ex.errorCode())) {
                // best-effort: parse definitionId from stored json
                String msg = ex.getMessage();
                long id = Long.parseLong(msg.replaceAll("(?s).*\"definitionId\":(\\d+).*", "$1"));
                return toDefinitionView(queryService.getDefinition(id));
            }
            throw ex;
        }
    }

    public TaskDefinitionView getDefinition(long definitionId) {
        return toDefinitionView(queryService.getDefinition(definitionId));
    }

    /** E06: pause / resume / retire a definition. */
    public CommandResultView executeDefinitionCommand(long definitionId, String commandKey, DefinitionCommandRequest req) {
        var result = definitionCommandService.execute(definitionId, commandKey, req.requestId());
        var defView = toDefinitionView(queryService.getDefinition(definitionId));
        JsonNode defNode = objectMapper.valueToTree(defView);
        return new CommandResultView(
                "DEFINITION",
                String.valueOf(definitionId),
                result.revision(),
                !result.commandKey().equals("no_change"),
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
                instanceId, commandKey, req.commandSchemaVersion(), req.requestId(), payloadJson);

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
        int ready = 0, succeeded = 0, total = d.actions().size();
        for (var a : d.actions()) {
            if ("READY".equals(a.status())) ready++;
            if ("SUCCEEDED".equals(a.status())) succeeded++;
        }
        String deliveryState = total == 0 ? "NONE" : (succeeded == total ? "COMPLETE" : "PENDING");
        DeliverySummary delivery = new DeliverySummary(
                deliveryState,
                total,
                ready,
                0,
                0,
                succeeded,
                0,
                0,
                0,
                0,
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
}
