package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.extension.action.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker: claims READY action jobs and executes LOCAL_TRANSACTIONAL handlers.
 *
 * For each due action:
 * 1. Load the full action record and re-check definition control barrier.
 * 2. If LOCAL_TRANSACTIONAL: execute handler and mark SUCCEEDED/FAILED in one transaction.
 */
@Component
public class MxzActionWorker {

    private static final Logger log = LoggerFactory.getLogger(MxzActionWorker.class);
    private static final int CLAIM_BATCH = 20;

    private final ActionJobExecutionPort actionPort;
    private final TaskDefinitionRepository definitionRepository;
    private final ExtensionRegistry extensionRegistry;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzActionWorker(
            ActionJobExecutionPort actionPort,
            TaskDefinitionRepository definitionRepository,
            ExtensionRegistry extensionRegistry,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.actionPort = actionPort;
        this.definitionRepository = definitionRepository;
        this.extensionRegistry = extensionRegistry;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6000)
    public void pollAndExecute() {
        try {
            List<Long> ids = actionPort.listReadyDueIds(clock.nowUtcSeconds(), CLAIM_BATCH);
            for (Long actionJobId : ids) {
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

    /** Visible for IT: run one action through the same barrier + execute path as the poller. */
    public void executeAction(long actionJobId) {
        tx.execute(() -> {
            var actionOpt = actionPort.findByIdForUpdate(actionJobId);
            if (actionOpt.isEmpty()) return null;
            var action = actionOpt.get();
            if (!"READY".equals(action.status()) && !"RETRY_WAIT".equals(action.status())) return null;
            if (!"LOCAL_TRANSACTIONAL".equals(action.executionMode())) return null;

            var defOpt = definitionRepository.findById(action.definitionId());
            if (defOpt.isEmpty()) {
                actionPort.markCancelled(actionJobId, "DEFINITION_MISSING", clock.nowUtcSeconds());
                return null;
            }
            var def = defOpt.get();
            if (action.definitionControlGeneration() != def.controlGeneration()
                    || def.controlState() == ControlState.PAUSED
                    || def.controlState() == ControlState.RETIRED) {
                actionPort.markCancelled(actionJobId, "CONTROL_BARRIER", clock.nowUtcSeconds());
                return null;
            }

            var handlerKey = new ActionHandlerKey(action.handlerKey(), action.schemaVersion());
            var handlerOpt = extensionRegistry.actionHandlers().find(handlerKey);
            if (handlerOpt.isEmpty()) {
                log.warn("Action worker: no handler for key {}/{}", action.handlerKey(), action.schemaVersion());
                return null;
            }

            String token = UUID.randomUUID().toString();
            Map<String, Object> payloadFields = parsePayload(action.payloadJson());
            payloadFields.put("tenantId", action.tenantId());
            payloadFields.put("recipientType", action.targetType());
            payloadFields.put("recipientId", action.targetId());

            var execCtx = new MxzActionExecutionContext(
                    action.actionJobId(),
                    action.definitionId(),
                    action.instanceId(),
                    action.transitionId(),
                    action.actionKey(),
                    action.schemaVersion(),
                    new MxzJsonPayload(payloadFields),
                    token);

            var result = handlerOpt.get().execute(execCtx);
            if (result.outcome() == ActionHandlerOutcome.SUCCEEDED) {
                actionPort.markSucceeded(actionJobId, result.outcomeCode(),
                        result.safeSummary(), clock.nowUtcSeconds());
            } else {
                log.warn("Action worker: action {} failed: {} {}", actionJobId,
                        result.outcomeCode(), result.safeSummary());
            }
            return null;
        });
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
