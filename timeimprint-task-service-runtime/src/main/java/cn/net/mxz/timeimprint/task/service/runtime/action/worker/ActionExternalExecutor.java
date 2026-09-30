package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EXTERNAL Action execution path (claim → effectStartedAt → out-of-TX call → result TX).
 * Change reason: irreversible external side-effect protocol independent of LOCAL TX path.
 */
@Component
public class ActionExternalExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExternalExecutor.class);

    private final ActionJobExecutionPort actionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final RuntimeAdmission admission;
    private final ActionExecutionSupport support;

    public ActionExternalExecutor(
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

    public void execute(long actionJobId) {
        Instant now = clock.nowUtcSeconds();
        Instant leaseUntil = now.plus(ActionExecutionSupport.LEASE_SECONDS, ChronoUnit.SECONDS);

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

            if (support.isBarrierBlocked(action.definitionId(), action.definitionControlGeneration())) {
                actionPort.markCancelled(actionJobId, "CONTROL_BARRIER", clock.nowUtcSeconds());
                return null;
            }
            var handlerOpt = support.extensionRegistry()
                    .actionHandlers()
                    .find(new ActionHandlerKey(action.handlerKey(), action.schemaVersion()));
            if (handlerOpt.isEmpty()) {
                log.warn("Action worker: no EXTERNAL handler for {}/{}", action.handlerKey(), action.schemaVersion());
                return null;
            }
            ActionHandler externalHandler = handlerOpt.get();
            if (externalHandler.timeoutSeconds() > ActionExecutionSupport.MAX_HANDLER_TIMEOUT_SECONDS) {
                log.warn(
                        "Action worker: EXTERNAL handler {} timeoutSeconds {} exceeds lease safety max {}",
                        action.handlerKey(),
                        externalHandler.timeoutSeconds(),
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
            if (support.isPolicyBlocked(defOpt.get(), instOpt.get(), actionJobId)) {
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

        ActionHandler handler = support.extensionRegistry()
                .actionHandlers()
                .require(new ActionHandlerKey(claimed.handlerKey(), claimed.schemaVersion()));
        if (handler.executionMode() != ActionExecutionMode.EXTERNAL) {
            log.warn("Action worker: handler mode mismatch for {}", claimed.handlerKey());
            return;
        }

        Map<String, Object> fields = support.parsePayload(claimed.payloadJson());
        fields.put("tenantId", claimed.tenantId());
        fields.put("recipientType", claimed.targetType());
        fields.put("recipientId", claimed.targetId());
        var execCtx = new ActionExecutionContext(
                actionJobId,
                claimed.definitionId(),
                claimed.instanceId(),
                claimed.transitionId(),
                claimed.actionKey(),
                claimed.schemaVersion(),
                new JsonPayload(fields),
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

}
