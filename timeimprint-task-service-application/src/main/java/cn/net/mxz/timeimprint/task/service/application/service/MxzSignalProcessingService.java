package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzSignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MxzSignalProcessingService {

    private final TaskSignalRepository signalRepository;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ParticipantQuery participantQuery;
    private final TransitionPlanCommitter committer;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final ExtensionRegistry extensionRegistry;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzSignalProcessingService(
            TaskSignalRepository signalRepository,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ParticipantQuery participantQuery,
            TransitionPlanCommitter committer,
            ActionJobExecutionPort actionJobExecutionPort,
            ExtensionRegistry extensionRegistry,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.signalRepository = signalRepository;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.participantQuery = participantQuery;
        this.committer = committer;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.extensionRegistry = extensionRegistry;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public void processSignal(long signalId) {
        tx.execute(() -> {
            var signal = signalRepository
                    .findByIdForUpdate(signalId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "signal"));
            if ("SUCCEEDED".equals(signal.processStatus()) || "IGNORED".equals(signal.processStatus())) {
                return null;
            }
            var def = definitionRepository
                    .findByIdForUpdate(signal.definitionId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (signal.instanceId() == null) {
                throw new MxzApplicationException("INVALID_REQUEST", "calendar signal requires instance");
            }
            var inst = instanceRepository
                    .findByIdForUpdate(signal.instanceId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
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
            List<String> recipients = collectRecipients(participants);
            if (recipients.isEmpty()) {
                // Fixture/poison rows without OWNER/RECIPIENT must not stay READY forever.
                signalRepository.markIgnored(signalId, "NO_RECIPIENTS", "no recipients", clock.nowUtcSeconds());
                return null;
            }
            payloadFields.putIfAbsent("recipientType", "USER");
            payloadFields.putIfAbsent("recipientId", recipients.get(0));
            // For multi-recipient, scenario emits one; expand later if needed
            var result = ext.processSignal(new MxzSignalProcessContext(
                    def,
                    inst,
                    signal.signalId(),
                    signal.providerKey(),
                    signal.signalKey(),
                    signal.schemaVersion(),
                    new MxzJsonPayload(payloadFields)));
            Instant now = clock.nowUtcSeconds();
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
                    var commit = committer.commit(new MxzTransitionCommitRequest(
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
                        var exec = handler.execute(new MxzActionExecutionContext(
                                action.actionJobId(),
                                action.definitionId(),
                                action.instanceId(),
                                action.transitionId(),
                                action.actionKey(),
                                action.schemaVersion(),
                                new MxzJsonPayload(actionPayload),
                                token));
                        if (exec.outcome() == ActionHandlerOutcome.SUCCEEDED) {
                            actionJobExecutionPort.markSucceeded(
                                    action.actionJobId(), exec.outcomeCode(), exec.safeSummary(), now);
                        } else {
                            throw new MxzApplicationException(
                                    "INTERNAL_ERROR", "action failed: " + exec.outcomeCode());
                        }
                    }
                    signalRepository.markSucceeded(signalId, "APPLIED", "transition " + commit.transitionId(), now);
                }
            }
            return null;
        });
    }

    private TransitionPlan expandRecipients(
            TransitionPlan plan, String tenantId, String scenarioKey, List<MxzParticipantRecord> participants) {
        if (plan.actionJobIntents().isEmpty()) {
            return plan;
        }
        List<String> recipients = resolveRecipients(participants);
        List<ActionJobIntent> expanded = new ArrayList<>();
        for (ActionJobIntent intent : plan.actionJobIntents()) {
            Map<String, Object> base = new HashMap<>();
            if (intent.payload() instanceof MxzJsonPayload json) {
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
                String actionKey = purpose + ":" + base64Url(MxzSha256.digestUtf8(canon));
                expanded.add(new ActionJobIntent(
                        intent.handlerKey(),
                        intent.actionSchemaVersion(),
                        actionKey,
                        intent.executionMode(),
                        "USER",
                        recipientId,
                        intent.availableAt(),
                        intent.expiresAt(),
                        new MxzJsonPayload(fields)));
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
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "action " + actionJobId));
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
            var exec = handler.execute(new MxzActionExecutionContext(
                    action.actionJobId(),
                    action.definitionId(),
                    action.instanceId(),
                    action.transitionId(),
                    action.actionKey(),
                    action.schemaVersion(),
                    new MxzJsonPayload(actionPayload),
                    executionToken));
            Instant now = clock.nowUtcSeconds();
            if (exec.outcome() == ActionHandlerOutcome.SUCCEEDED) {
                actionJobExecutionPort.markSucceeded(action.actionJobId(), exec.outcomeCode(), exec.safeSummary(), now);
            } else {
                throw new MxzApplicationException("INTERNAL_ERROR", "action failed: " + exec.outcomeCode());
            }
            return null;
        });
    }

    private static List<String> resolveRecipients(List<MxzParticipantRecord> participants) {
        List<String> finalRecipients = collectRecipients(participants);
        if (finalRecipients.isEmpty()) {
            throw new MxzApplicationException("INVALID_REQUEST", "no recipients");
        }
        if (finalRecipients.size() > 10) {
            throw new MxzApplicationException("INVALID_REQUEST", "too many recipients");
        }
        return finalRecipients;
    }

    private static List<String> collectRecipients(List<MxzParticipantRecord> participants) {
        Set<String> recipients = new LinkedHashSet<>();
        Set<String> owners = new LinkedHashSet<>();
        for (var p : participants) {
            if ("RECIPIENT".equals(p.roleCode())) {
                recipients.add(p.principalId());
            } else if ("OWNER".equals(p.roleCode())) {
                owners.add(p.principalId());
            }
        }
        return recipients.isEmpty() ? List.copyOf(owners) : List.copyOf(recipients);
    }

    private Map<String, Object> parse(String json) {
        try {
            return new HashMap<>(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "bad payload json");
        }
    }

    private static String base64Url(byte[] dig) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
    }
}
