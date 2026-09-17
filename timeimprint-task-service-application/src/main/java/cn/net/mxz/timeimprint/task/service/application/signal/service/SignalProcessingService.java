package cn.net.mxz.timeimprint.task.service.application.signal.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.instance.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.application.inbox.validation.RecipientRules;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SignalProcessingService {

    private static final int LEASE_SECONDS = 30;

    private final TaskSignalRepository signalRepository;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ParticipantQuery participantQuery;
    private final TransitionPlanCommitter committer;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final InstanceCommandPort instanceCommandPort;
    private final ExtensionRegistry extensionRegistry;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final JsonMapper objectMapper;
    private final TriggerBindingQuery triggerBindingQuery;

    public SignalProcessingService(
            TaskSignalRepository signalRepository,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ParticipantQuery participantQuery,
            TransitionPlanCommitter committer,
            ActionJobExecutionPort actionJobExecutionPort,
            InstanceCommandPort instanceCommandPort,
            ExtensionRegistry extensionRegistry,
            TransactionBoundary tx,
            BusinessClock clock,
            JsonMapper objectMapper,
            TriggerBindingQuery triggerBindingQuery) {
        this.signalRepository = signalRepository;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.participantQuery = participantQuery;
        this.committer = committer;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.instanceCommandPort = instanceCommandPort;
        this.extensionRegistry = extensionRegistry;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.triggerBindingQuery = triggerBindingQuery;
    }

    public void processSignal(long signalId) {
        String token = tx.execute(() -> tryClaim(signalId));
        if (token == null) {
            return;
        }
        tx.execute(() -> {
            processClaimed(signalId, token);
            return null;
        });
    }

    private String tryClaim(long signalId) {
        Instant now = clock.nowUtcSeconds();
        String owner = Optional.ofNullable(System.getenv("INSTANCE_ID")).orElse("signal-worker");
        Instant leaseUntil = now.plus(LEASE_SECONDS, ChronoUnit.SECONDS);
        return signalRepository.claimForProcessing(signalId, owner, leaseUntil, now).orElse(null);
    }

    private void processClaimed(long signalId, String expectedToken) {
        var peek = signalRepository
                .findById(signalId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "signal"));
        var def = definitionRepository
                .findByIdForUpdate(peek.definitionId())
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
        if (peek.triggerBindingId() != null) {
            triggerBindingQuery
                    .findByIdForUpdate(peek.triggerBindingId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "trigger binding"));
        }
        if (peek.instanceId() == null) {
            throw new ApplicationException("INVALID_REQUEST", "calendar signal requires instance");
        }
        instanceRepository
                .findByIdForUpdate(peek.instanceId())
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
        var signal = signalRepository
                .findByIdForUpdate(signalId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "signal"));
        if (!"RUNNING".equals(signal.processStatus())
                || signal.executionToken() == null
                || !expectedToken.equals(signal.executionToken())) {
            throw new ApplicationException("STATE_CONFLICT", "signal token lost");
        }
            Instant now = clock.nowUtcSeconds();
            if (signal.definitionControlGeneration() != def.controlGeneration()) {
                signalRepository.markIgnored(
                        signalId, "CONTROL_GENERATION_MISMATCH", "stale control generation", now);
                return;
            }
            if (def.controlState() == ControlState.PAUSED || def.controlState() == ControlState.RETIRED) {
                signalRepository.markIgnored(
                        signalId, "CONTROL_STATE_BLOCKED", "definition " + def.controlState(), now);
                return;
            }
            var inst = instanceRepository
                    .findByIdForUpdate(signal.instanceId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
            var ext = extensionRegistry
                    .scenarioExtensions()
                    .require(new ScenarioExtensionKey(def.scenarioKey(), 1));
            Map<String, Object> payloadFields = parse(signal.payloadJson());
            payloadFields.putIfAbsent("title", inst.titleSnapshot());
            payloadFields.putIfAbsent("body", inst.descriptionSnapshot() == null ? "" : inst.descriptionSnapshot());
            payloadFields.putIfAbsent("tenantId", def.tenantId());
            payloadFields.putIfAbsent("definitionId", def.definitionId());
            payloadFields.putIfAbsent("instanceId", inst.instanceId());
            if (inst.occurrenceAt() != null) {
                payloadFields.putIfAbsent("occurrenceAt", inst.occurrenceAt().toString());
            }
            var participants = participantQuery.listDefinitionLevel(def.definitionId());
            List<String> recipients;
            try {
                recipients = RecipientRules.resolve(participants);
            } catch (ApplicationException ex) {
                signalRepository.markIgnored(signalId, ex.errorCode(), ex.getMessage(), now);
                return;
            }
            payloadFields.putIfAbsent("recipientType", "USER");
            payloadFields.putIfAbsent("recipientId", recipients.get(0));
            // For multi-recipient, scenario emits one; expand later if needed
            var result = ext.processSignal(new SignalProcessContext(
                    def,
                    inst,
                    signal.signalId(),
                    signal.providerKey(),
                    signal.signalKey(),
                    signal.schemaVersion(),
                    new JsonPayload(payloadFields)));
            switch (result) {
                case HandlerResult.NoChange nc -> {
                    signalRepository.markIgnored(signalId, "NO_CHANGE", String.valueOf(nc.result()), now);
                }
                case HandlerResult.Rejected rejected -> {
                    signalRepository.markIgnored(signalId, rejected.reasonCode(), rejected.safeMessage(), now);
                }
                case HandlerResult.Applied applied -> {
                    TransitionPlan expanded = applied.plan();
                    boolean needsExpand = expanded.actionJobIntents().stream()
                            .anyMatch(a -> a.targetId() == null || "INITIAL:PENDING_EXPAND".equals(a.actionKey()));
                    if (needsExpand) {
                        expanded = expandRecipients(
                                applied.plan(),
                                def.tenantId(),
                                def.scenarioKey(),
                                participantQuery.listDefinitionLevel(def.definitionId()));
                    }
                    var commit = committer.commit(new TransitionCommitRequest(
                            expanded, def.definitionId(), inst.instanceId(), "SIGNAL", signal.signalKey()));
                    // execute due LOCAL_TRANSACTIONAL actions created by this transition
                    var actions = actionJobExecutionPort.listByInstance(inst.instanceId());
                    for (var action : actions) {
                        if (!"READY".equals(action.status())) {
                            continue;
                        }
                        if (!"LOCAL_TRANSACTIONAL".equals(action.executionMode())) {
                            continue;
                        }
                        if (action.transitionId() != commit.transitionId()) {
                            continue;
                        }
                        if (action.expiresAt() != null && !action.expiresAt().isAfter(now)) {
                            actionJobExecutionPort.expireIfDue(action.actionJobId(), now);
                            continue;
                        }
                        if (action.availableAt() != null && action.availableAt().isAfter(now)) {
                            continue; // leave for Action Worker when availableAt arrives
                        }
                        var handler = extensionRegistry
                                .actionHandlers()
                                .require(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
                        Map<String, Object> actionPayload = parse(action.payloadJson());
                        actionPayload.put("tenantId", action.tenantId());
                        actionPayload.put("recipientType", action.targetType());
                        actionPayload.put("recipientId", action.targetId());
                        actionPayload.put("scenarioKey", def.scenarioKey());
                        String token = UUID.randomUUID().toString();
                        var exec = handler.execute(new ActionExecutionContext(
                                action.actionJobId(),
                                action.definitionId(),
                                action.instanceId(),
                                action.transitionId(),
                                action.actionKey(),
                                action.schemaVersion(),
                                new JsonPayload(actionPayload),
                                token));
                        if (exec.outcome() == ActionHandlerOutcome.SUCCEEDED) {
                            actionJobExecutionPort.markSucceeded(
                                    action.actionJobId(), exec.outcomeCode(), exec.safeSummary(), now);
                        } else {
                            throw new ApplicationException(
                                    "INTERNAL_ERROR", "action failed: " + exec.outcomeCode());
                        }
                    }
                    var planTransition = expanded.instanceStateTransition();
                    if (planTransition != null
                            && planTransition.toLifecycleCategory() == LifecycleCategory.TERMINAL) {
                        instanceCommandPort.cancelRemainingActionsExceptTransition(
                                inst.instanceId(), commit.transitionId(), now);
                    }
                    signalRepository.markSucceeded(signalId, "APPLIED", "transition " + commit.transitionId(), now);
                }
            }
    }

    private TransitionPlan expandRecipients(
            TransitionPlan plan, String tenantId, String scenarioKey, List<ParticipantRecord> participants) {
        if (plan.actionJobIntents().isEmpty()) {
            return plan;
        }
        List<String> recipients = RecipientRules.resolve(participants);
        List<ActionJobIntent> expanded = new ArrayList<>();
        for (ActionJobIntent intent : plan.actionJobIntents()) {
            Map<String, Object> base = new HashMap<>();
            if (intent.payload() instanceof JsonPayload json) {
                base.putAll(json.fields());
            }
            base.put("tenantId", tenantId);
            base.put("scenarioKey", scenarioKey);
            for (String recipientId : recipients) {
                Map<String, Object> fields = new HashMap<>(base);
                fields.put("recipientType", "USER");
                fields.put("recipientId", recipientId);
                String purpose = String.valueOf(fields.getOrDefault("purpose", "INITIAL"));
                int slot = ((Number) fields.getOrDefault("slotIndex", 0)).intValue();
                int gen = ((Number) fields.getOrDefault("actionGeneration", 1)).intValue();
                String canon = fields.get("instanceId")
                        + ":"
                        + purpose
                        + ":"
                        + slot
                        + ":"
                        + gen
                        + ":"
                        + recipientId
                        + ":in_app_notification";
                String actionKey = purpose + ":" + base64Url(Sha256.digestUtf8(canon));
                expanded.add(new ActionJobIntent(
                        intent.handlerKey(),
                        intent.actionSchemaVersion(),
                        actionKey,
                        intent.executionMode(),
                        "USER",
                        recipientId,
                        intent.availableAt(),
                        intent.expiresAt(),
                        new JsonPayload(fields)));
            }
        }
        return new TransitionPlan(
                plan.target(),
                plan.definitionControlTransition(),
                plan.instanceStateTransition(),
                plan.participantChanges(),
                expanded,
                plan.triggerBindingChanges(),
                plan.plannedSignalIntents(),
                plan.scenarioDataMutations(),
                plan.auditSummary());
    }

    /**
     * Execute an already-claimed action job (Action Worker path for non-inline actions).
     * For LOCAL_TRANSACTIONAL actions that were inline-executed during signal processing,
     * this is a no-op. For ASYNC/EXTERNAL actions this would call the handler.
     */
    public void executeClaimedAction(long actionJobId, String executionToken) {
        tx.execute(() -> {
            var action = actionJobExecutionPort.findByIdForUpdate(actionJobId)
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "action " + actionJobId));
            if (!"READY".equals(action.status()) && !"PROCESSING".equals(action.status())) {
                return null; // already handled
            }
            if (!"LOCAL_TRANSACTIONAL".equals(action.executionMode())) {
                return null; // only handle LOCAL_TRANSACTIONAL inline for now
            }
            var handler = extensionRegistry.actionHandlers()
                    .require(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
            Map<String, Object> actionPayload = parse(action.payloadJson());
            actionPayload.put("tenantId", action.tenantId());
            actionPayload.put("recipientType", action.targetType());
            actionPayload.put("recipientId", action.targetId());
            var exec = handler.execute(new ActionExecutionContext(
                    action.actionJobId(),
                    action.definitionId(),
                    action.instanceId(),
                    action.transitionId(),
                    action.actionKey(),
                    action.schemaVersion(),
                    new JsonPayload(actionPayload),
                    executionToken));
            Instant now = clock.nowUtcSeconds();
            if (exec.outcome() == ActionHandlerOutcome.SUCCEEDED) {
                actionJobExecutionPort.markSucceeded(action.actionJobId(), exec.outcomeCode(), exec.safeSummary(), now);
            } else {
                throw new ApplicationException("INTERNAL_ERROR", "action failed: " + exec.outcomeCode());
            }
            return null;
        });
    }

    private Map<String, Object> parse(String json) {
        try {
            return new HashMap<>(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "bad payload json");
        }
    }

    private static String base64Url(byte[] dig) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
    }
}
