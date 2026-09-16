package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LOCAL_TRANSACTIONAL Action execution path.
 * Change reason: in-TX barrier + handler + CAS close independent of EXTERNAL effectStartedAt flow.
 */
@Component
public class ActionLocalExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionLocalExecutor.class);

    private final ActionJobExecutionPort actionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final RuntimeAdmission admission;
    private final ActionExecutionSupport support;

    public ActionLocalExecutor(
            ActionJobExecutionPort actionPort,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            TransactionBoundary tx,
            BusinessClock clock,
            RuntimeAdmission admission,
            ActionExecutionSupport support) {
        this.actionPort = actionPort;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.tx = tx;
        this.clock = clock;
        this.admission = admission;
        this.support = support;
    }

    public void execute(long actionJobId, boolean forceCasMismatch) {
        Instant now = clock.nowUtcSeconds();
        Instant leaseUntil = now.plus(ActionExecutionSupport.LEASE_SECONDS, ChronoUnit.SECONDS);
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

            var handlerOpt = support.extensionRegistry()
                    .actionHandlers()
                    .find(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
            if (handlerOpt.isEmpty()) {
                log.warn("Action worker: no handler for key {}/{}", action.handlerKey(), action.schemaVersion());
                return null;
            }
            ActionHandler handler = handlerOpt.get();
            if (handler.timeoutSeconds() > ActionExecutionSupport.MAX_HANDLER_TIMEOUT_SECONDS) {
                log.warn(
                        "Action worker: handler {} timeoutSeconds {} exceeds lease safety max {}",
                        action.handlerKey(),
                        handler.timeoutSeconds(),
                        ActionExecutionSupport.MAX_HANDLER_TIMEOUT_SECONDS);
                actionPort.markCancelled(actionJobId, "HANDLER_TIMEOUT_MISCONFIGURED", now);
                return null;
            }

            if (!admission.acceptingClaims()) {
                return null;
            }

            var tokenOpt = actionPort.claimForExecution(actionJobId, ActionExecutionSupport.LEASE_OWNER, leaseUntil, now);
            if (tokenOpt.isEmpty()) {
                return null;
            }
            String token = tokenOpt.get();
            action = actionPort.findByIdForUpdate(actionJobId).orElse(action);

            if (support.isPolicyBlocked(def, instOpt.get(), actionJobId)) {
                actionPort.releasePolicyBlocked(actionJobId, token, "POLICY_BLOCKED", now);
                return null;
            }

            var execCtx = support.buildContext(action, token);
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

}
