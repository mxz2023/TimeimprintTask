package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.limit.MxzPlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.runtime.MxzRuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzPolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyDecision;
import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.extension.spi.Policy;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker for READY Action jobs.
 *
 * <p>LOCAL_TRANSACTIONAL: barrier + execute + result in one short transaction.
 * EXTERNAL: claim → effectStartedAt barrier TX → call outside TX → result TX (pause cannot revoke).
 */
@Component
public class MxzActionWorker {

    private static final Logger log = LoggerFactory.getLogger(MxzActionWorker.class);
    private static final int CLAIM_BATCH = 20;
    /** Default lease; must match {@code LEASE_SECONDS} config (03). */
    private static final int LEASE_SECONDS = 30;
    /**
     * Handler {@code timeoutSeconds} must be ≤ {@code LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS}
     * (03 {@code ACTION_LEASE_SAFETY_SECONDS}, default 5 → max 25 with 30s lease).
     */
    private static final int MAX_HANDLER_TIMEOUT_SECONDS =
            LEASE_SECONDS - MxzPlatformLimits.ACTION_LEASE_SAFETY_SECONDS;
    private static final String LEASE_OWNER = "action-worker";

    private final ActionJobExecutionPort actionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ExtensionRegistry extensionRegistry;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;
    private final MxzRuntimeAdmission admission;

    public MxzActionWorker(
            ActionJobExecutionPort actionPort,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ExtensionRegistry extensionRegistry,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper,
            MxzRuntimeAdmission admission) {
        this.actionPort = actionPort;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.extensionRegistry = extensionRegistry;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.admission = admission;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6000)
    public void pollAndExecute() {
        if (!admission.acceptingClaims()) {
            return;
        }
        try {
            List<Long> ids = actionPort.listReadyDueIds(clock.nowUtcSeconds(), CLAIM_BATCH);
            for (Long actionJobId : ids) {
                if (!admission.acceptingClaims()) {
                    return;
                }
                try {
                    executeAction(actionJobId);
                } catch (Exception e) {
                    log.warn("Action worker: error executing action {}: {}", actionJobId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Action worker: poll error: {}", e.getMessage());
        }
    }

    /** Visible for IT: run one action through the same path as the poller. */
    public void executeAction(long actionJobId) {
        var peek = actionPort.findById(actionJobId);
        if (peek.isEmpty()) {
            return;
        }
        if ("EXTERNAL".equals(peek.get().executionMode())) {
            executeExternal(actionJobId);
        } else {
            executeLocal(actionJobId);
        }
    }

    private void executeLocal(long actionJobId) {
        executeLocalInternal(actionJobId, false);
    }

    /**
     * IT hook for A10: write LOCAL effect then complete with a mismatched token so CAS fails and
     * the whole transaction (inbox + attempt + claim) rolls back.
     */
    public void executeLocalWithForcedCasFailure(long actionJobId) {
        executeLocalInternal(actionJobId, true);
    }

    private void executeLocalInternal(long actionJobId, boolean forceCasMismatch) {
        Instant now = clock.nowUtcSeconds();
        Instant leaseUntil = now.plus(LEASE_SECONDS, ChronoUnit.SECONDS);
        tx.execute(() -> {
            var peek = actionPort.findById(actionJobId);
            if (peek.isEmpty()) return null;
            // Lock order: definition → instance → Action → Attempt (claim inserts Attempt).
            var defOpt = definitionRepository.findByIdForUpdate(peek.get().definitionId());
            var instOpt = instanceRepository.findByIdForUpdate(peek.get().instanceId());
            var actionOpt = actionPort.findByIdForUpdate(actionJobId);
            if (defOpt.isEmpty() || instOpt.isEmpty() || actionOpt.isEmpty()) return null;
            var action = actionOpt.get();
            if (!"READY".equals(action.status()) && !"RETRY_WAIT".equals(action.status())) return null;
            if (!"LOCAL_TRANSACTIONAL".equals(action.executionMode())) return null;

            if (actionPort.expireIfDue(actionJobId, now)) {
                return null;
            }

            var def = defOpt.get();
            if (action.definitionControlGeneration() != def.controlGeneration()
                    || def.controlState() == ControlState.PAUSED
                    || def.controlState() == ControlState.RETIRED) {
                actionPort.markCancelled(actionJobId, "CONTROL_BARRIER", now);
                return null;
            }

            var handlerOpt = extensionRegistry
                    .actionHandlers()
                    .find(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
            if (handlerOpt.isEmpty()) {
                log.warn("Action worker: no handler for key {}/{}", action.handlerKey(), action.schemaVersion());
                return null;
            }
            ActionHandler handler = handlerOpt.get();
            if (handler.timeoutSeconds() > MAX_HANDLER_TIMEOUT_SECONDS) {
                log.warn(
                        "Action worker: handler {} timeoutSeconds {} exceeds lease safety max {}",
                        action.handlerKey(),
                        handler.timeoutSeconds(),
                        MAX_HANDLER_TIMEOUT_SECONDS);
                actionPort.markCancelled(actionJobId, "HANDLER_TIMEOUT_MISCONFIGURED", now);
                return null;
            }

            if (!admission.acceptingClaims()) {
                return null;
            }

            var tokenOpt = actionPort.claimForExecution(actionJobId, LEASE_OWNER, leaseUntil, now);
            if (tokenOpt.isEmpty()) {
                return null;
            }
            String token = tokenOpt.get();
            action = actionPort.findByIdForUpdate(actionJobId).orElse(action);

            if (isPolicyBlocked(def, instOpt.get(), actionJobId)) {
                actionPort.releasePolicyBlocked(actionJobId, token, "POLICY_BLOCKED", now);
                return null;
            }

            var execCtx = buildContext(action, token);
            var result = handler.execute(execCtx);
            Instant done = clock.nowUtcSeconds();
            if (result.outcome() == ActionHandlerOutcome.RETRYABLE_FAILURE) {
                actionPort.completeRetryableFailure(
                        actionJobId, token, result.outcomeCode(), result.safeSummary(), done);
                return null;
            }
            if (result.outcome() != ActionHandlerOutcome.SUCCEEDED) {
                throw new IllegalStateException(
                        "LOCAL handler failed: " + result.outcomeCode() + " " + result.safeSummary());
            }

            actionPort.completeAttempt(
                    actionJobId,
                    token,
                    "SUCCEEDED",
                    null,
                    result.outcomeCode(),
                    result.safeSummary(),
                    done);
            String casToken = forceCasMismatch ? token + "-mismatch" : token;
            boolean closed = actionPort.completeWithToken(
                    actionJobId, casToken, "SUCCEEDED", result.outcomeCode(), result.safeSummary(), done);
            if (!closed) {
                // A10: effect already written in this TX — must roll back with the CAS miss.
                throw new IllegalStateException("CAS_FAILED");
            }
            return null;
        });
    }

    private void executeExternal(long actionJobId) {
        Instant now = clock.nowUtcSeconds();
        Instant leaseUntil = now.plus(LEASE_SECONDS, ChronoUnit.SECONDS);

        record ClaimPack(String token, long definitionId, long instanceId, long transitionId, long controlGen,
                String handlerKey, int schemaVersion, String actionKey, String tenantId,
                String targetType, String targetId, String payloadJson) {}

        ClaimPack claimed = tx.execute(() -> {
            var peek = actionPort.findById(actionJobId);
            if (peek.isEmpty()) return null;
            if (!"EXTERNAL".equals(peek.get().executionMode())) return null;

            var defOpt = definitionRepository.findByIdForUpdate(peek.get().definitionId());
            var instOpt = instanceRepository.findByIdForUpdate(peek.get().instanceId());
            var actionOpt = actionPort.findByIdForUpdate(actionJobId);
            if (defOpt.isEmpty() || instOpt.isEmpty() || actionOpt.isEmpty()) return null;
            var action = actionOpt.get();
            if (!"READY".equals(action.status()) && !"RETRY_WAIT".equals(action.status())) return null;
            if (!"EXTERNAL".equals(action.executionMode())) return null;

            if (actionPort.expireIfDue(actionJobId, now)) {
                return null;
            }

            if (isBarrierBlocked(action.definitionId(), action.definitionControlGeneration())) {
                actionPort.markCancelled(actionJobId, "CONTROL_BARRIER", clock.nowUtcSeconds());
                return null;
            }
            var handlerOpt = extensionRegistry
                    .actionHandlers()
                    .find(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
            if (handlerOpt.isEmpty()) {
                log.warn("Action worker: no EXTERNAL handler for {}/{}", action.handlerKey(), action.schemaVersion());
                return null;
            }
            ActionHandler externalHandler = handlerOpt.get();
            if (externalHandler.timeoutSeconds() > MAX_HANDLER_TIMEOUT_SECONDS) {
                log.warn(
                        "Action worker: EXTERNAL handler {} timeoutSeconds {} exceeds lease safety max {}",
                        action.handlerKey(),
                        externalHandler.timeoutSeconds(),
                        MAX_HANDLER_TIMEOUT_SECONDS);
                actionPort.markCancelled(actionJobId, "HANDLER_TIMEOUT_MISCONFIGURED", now);
                return null;
            }

            if (!admission.acceptingClaims()) {
                return null;
            }

            var tokenOpt = actionPort.claimForExecution(actionJobId, LEASE_OWNER, leaseUntil, now);
            if (tokenOpt.isEmpty()) {
                return null;
            }
            String token = tokenOpt.get();
            if (isPolicyBlocked(defOpt.get(), instOpt.get(), actionJobId)) {
                actionPort.releasePolicyBlocked(actionJobId, token, "POLICY_BLOCKED", clock.nowUtcSeconds());
                return null;
            }
            return new ClaimPack(
                    token,
                    action.definitionId(),
                    action.instanceId(),
                    action.transitionId(),
                    action.definitionControlGeneration(),
                    action.handlerKey(),
                    action.schemaVersion(),
                    action.actionKey(),
                    action.tenantId(),
                    action.targetType(),
                    action.targetId(),
                    action.payloadJson());
        });
        if (claimed == null) {
            return;
        }

        Boolean effectStarted = tx.execute(() -> {
            var defOpt = definitionRepository.findByIdForUpdate(claimed.definitionId());
            instanceRepository.findByIdForUpdate(claimed.instanceId());
            var actionOpt = actionPort.findByIdForUpdate(actionJobId);
            if (defOpt.isEmpty() || actionOpt.isEmpty()) {
                return false;
            }
            var def = defOpt.get();
            var action = actionOpt.get();
            if (!"RUNNING".equals(action.status())
                    || action.leaseUntil() == null
                    || action.leaseUntil().isBefore(clock.nowUtcSeconds())) {
                return false;
            }
            boolean blocked = claimed.controlGen() != def.controlGeneration()
                    || def.controlState() == ControlState.PAUSED
                    || def.controlState() == ControlState.RETIRED;
            if (blocked) {
                actionPort.cancelRunning(
                        actionJobId, claimed.token(), "CONTROL_BARRIER", clock.nowUtcSeconds());
                return false;
            }
            actionPort.markEffectStarted(actionJobId, claimed.token(), clock.nowUtcSeconds());
            return true;
        });
        if (!Boolean.TRUE.equals(effectStarted)) {
            return;
        }

        ActionHandler handler = extensionRegistry
                .actionHandlers()
                .require(new ActionHandlerKey(claimed.handlerKey(), claimed.schemaVersion()));
        if (handler.executionMode() != ActionExecutionMode.EXTERNAL) {
            log.warn("Action worker: handler mode mismatch for {}", claimed.handlerKey());
            return;
        }

        Map<String, Object> fields = parsePayload(claimed.payloadJson());
        fields.put("tenantId", claimed.tenantId());
        fields.put("recipientType", claimed.targetType());
        fields.put("recipientId", claimed.targetId());
        var execCtx = new MxzActionExecutionContext(
                actionJobId,
                claimed.definitionId(),
                claimed.instanceId(),
                claimed.transitionId(),
                claimed.actionKey(),
                claimed.schemaVersion(),
                new MxzJsonPayload(fields),
                claimed.token());

        var result = handler.execute(execCtx);
        Instant done = clock.nowUtcSeconds();
        String token = claimed.token();
        tx.execute(() -> {
            if (result.outcome() == ActionHandlerOutcome.RETRYABLE_FAILURE) {
                actionPort.completeRetryableFailure(
                        actionJobId, token, result.outcomeCode(), result.safeSummary(), done);
                return null;
            }
            String status =
                    switch (result.outcome()) {
                        case SUCCEEDED -> "SUCCEEDED";
                        case UNKNOWN -> "UNKNOWN";
                        case PERMANENT_FAILURE -> "DEAD";
                        case RETRYABLE_FAILURE -> "RETRY_WAIT";
                    };
            // Result TX must not revoke an already-started EXTERNAL call due to later pause.
            actionPort.completeAttempt(
                    actionJobId,
                    token,
                    result.outcome().name(),
                    null,
                    result.outcomeCode(),
                    result.safeSummary(),
                    done);
            boolean closed = actionPort.completeWithToken(
                    actionJobId, token, status, result.outcomeCode(), result.safeSummary(), done);
            if (!closed) {
                throw new IllegalStateException("EXTERNAL result CAS failed");
            }
            return null;
        });
    }

    private boolean isBarrierBlocked(long definitionId, long actionControlGen) {
        var defOpt = definitionRepository.findById(definitionId);
        if (defOpt.isEmpty()) {
            return true;
        }
        var def = defOpt.get();
        return actionControlGen != def.controlGeneration()
                || def.controlState() == ControlState.PAUSED
                || def.controlState() == ControlState.RETIRED;
    }

    /** True when any ACTION_EXECUTE Policy returns DENY or RETRY_LATER (refundable before effect). */
    private boolean isPolicyBlocked(
            MxzTaskDefinitionSnapshot def, MxzTaskInstanceSnapshot inst, long actionJobId) {
        var ctx = new MxzPolicyEvaluationContext(PolicyPhase.ACTION_EXECUTE, def, inst, null, actionJobId);
        for (Policy policy : extensionRegistry.policies().policiesForPhase(PolicyPhase.ACTION_EXECUTE)) {
            PolicyDecision decision = policy.evaluate(ctx);
            if (decision == PolicyDecision.DENY || decision == PolicyDecision.RETRY_LATER) {
                return true;
            }
        }
        return false;
    }

    private MxzActionExecutionContext buildContext(
            cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord action, String token) {
        Map<String, Object> payloadFields = parsePayload(action.payloadJson());
        payloadFields.put("tenantId", action.tenantId());
        payloadFields.put("recipientType", action.targetType());
        payloadFields.put("recipientId", action.targetId());
        return new MxzActionExecutionContext(
                action.actionJobId(),
                action.definitionId(),
                action.instanceId(),
                action.transitionId(),
                action.actionKey(),
                action.schemaVersion(),
                new MxzJsonPayload(payloadFields),
                token);
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {});
            return new HashMap<>(m);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
