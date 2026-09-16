package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.context.PolicyEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.policy.result.PolicyDecision;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.policy.spi.Policy;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Shared Action execution helpers (barrier, policy, context).
 * Change reason: Action claim/execute policy gates independent of LOCAL vs EXTERNAL paths.
 */
@Component
public class ActionExecutionSupport {

    /** Default lease; must match {@code LEASE_SECONDS} config (03). */
    public static final int LEASE_SECONDS = 30;
    /**
     * Handler {@code timeoutSeconds} must be ≤ {@code LEASE_SECONDS - ACTION_LEASE_SAFETY_SECONDS}
     * (03 {@code ACTION_LEASE_SAFETY_SECONDS}, default 5 → max 25 with 30s lease).
     */
    public static final int MAX_HANDLER_TIMEOUT_SECONDS =
            LEASE_SECONDS - PlatformLimits.ACTION_LEASE_SAFETY_SECONDS;
    public static final String LEASE_OWNER = "action-worker";

    private final TaskDefinitionRepository definitionRepository;
    private final ExtensionRegistry extensionRegistry;
    private final ObjectMapper objectMapper;

    public ActionExecutionSupport(
            TaskDefinitionRepository definitionRepository,
            ExtensionRegistry extensionRegistry,
            ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.extensionRegistry = extensionRegistry;
        this.objectMapper = objectMapper;
    }

    public boolean isBarrierBlocked(long definitionId, long actionControlGen) {
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
    public boolean isPolicyBlocked(TaskDefinitionSnapshot def, TaskInstanceSnapshot inst, long actionJobId) {
        var ctx = new PolicyEvaluationContext(PolicyPhase.ACTION_EXECUTE, def, inst, null, actionJobId);
        for (Policy policy : extensionRegistry.policies().policiesForPhase(PolicyPhase.ACTION_EXECUTE)) {
            PolicyDecision decision = policy.evaluate(ctx);
            if (decision == PolicyDecision.DENY || decision == PolicyDecision.RETRY_LATER) {
                return true;
            }
        }
        return false;
    }

    public ActionExecutionContext buildContext(ActionJobRecord action, String token) {
        Map<String, Object> payloadFields = parsePayload(action.payloadJson());
        payloadFields.put("tenantId", action.tenantId());
        payloadFields.put("recipientType", action.targetType());
        payloadFields.put("recipientId", action.targetId());
        return new ActionExecutionContext(
                action.actionJobId(),
                action.definitionId(),
                action.instanceId(),
                action.transitionId(),
                action.actionKey(),
                action.schemaVersion(),
                new JsonPayload(payloadFields),
                token);
    }

    public Map<String, Object> parsePayload(String json) {
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {});
            return new HashMap<>(m);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public ExtensionRegistry extensionRegistry() {
        return extensionRegistry;
    }
}
