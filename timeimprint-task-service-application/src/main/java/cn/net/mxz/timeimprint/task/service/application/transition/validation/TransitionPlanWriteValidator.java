package cn.net.mxz.timeimprint.task.service.application.transition.validation;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.Utf8LimitUtils;

/** Pre-write validation for TransitionPlan scale limits (A25/A29). */
public final class TransitionPlanWriteValidator {

    private TransitionPlanWriteValidator() {}

    public static void validateBeforeWrite(TransitionPlan plan, JsonMapper objectMapper) {
        int actions = plan.actionJobIntents().size();
        if (actions > PlatformLimits.MAX_ACTIONS_PER_TRANSITION) {
            throw limit("too many actions in transition");
        }
        if (actions > PlatformLimits.MAX_ACTIONS_PER_WRITE_TX) {
            throw limit("too many actions in write transaction");
        }
        int mutations = plan.scenarioDataMutations().size();
        if (mutations > PlatformLimits.MAX_SCENARIO_MUTATIONS) {
            throw limit("too many scenario mutations");
        }
        int mutationBytes = scenarioMutationBytes(plan);
        if (mutationBytes > PlatformLimits.MAX_SCENARIO_MUTATION_BYTES) {
            throw limit("scenario mutation payload too large");
        }
        int planBytes = transitionPlanBytes(plan, objectMapper);
        if (planBytes > PlatformLimits.MAX_TRANSITION_PLAN_BYTES) {
            throw limit("transition plan too large");
        }
        int rows = estimateMutatedRows(plan);
        if (rows > PlatformLimits.MAX_MUTATED_ROWS_PER_WRITE_TX) {
            throw limit("too many mutated rows estimated");
        }
        for (ActionJobIntent aj : plan.actionJobIntents()) {
            int payloadBytes = payloadUtf8Bytes(aj.payload());
            if (payloadBytes > PlatformLimits.MAX_JSON_VALUE_BYTES) {
                throw limit("action payload too large");
            }
        }
        for (PlannedSignalIntentCheck si : plannedSignalIntents(plan)) {
            if (si.payloadBytes() > PlatformLimits.MAX_JSON_VALUE_BYTES) {
                throw limit("signal payload too large");
            }
        }
    }

    /**
     * Rough row budget aligned with {@code TransitionPlanCommitterImpl} (unit-testable).
     */
    public static int estimateMutatedRows(TransitionPlan plan) {
        int rows = 0;
        if (plan.definitionControlTransition() != null) {
            rows += 1;
        }
        rows += plan.triggerBindingChanges().size();
        rows += plan.participantChanges().size();
        if (plan.instanceStateTransition() != null) {
            rows += 2; // transition + instance CAS
        } else if (plan.definitionControlTransition() != null
                || !plan.plannedSignalIntents().isEmpty()
                || !plan.actionJobIntents().isEmpty()
                || !plan.scenarioDataMutations().isEmpty()) {
            rows += 1; // definition-level transition
        }
        rows += plan.plannedSignalIntents().size();
        for (ActionJobIntent aj : plan.actionJobIntents()) {
            rows += 1;
            if ("in_app_notification".equals(aj.handlerKey())
                    || "feishu_im_notification".equals(aj.handlerKey())) {
                rows += 1; // notification row（多渠道共享时为保守上界）
            }
        }
        rows += plan.scenarioDataMutations().stream()
                .filter(m -> !"REPLACE_INSTANCE_SNAPSHOT".equals(m.mutationKey()))
                .count();
        if (!plan.auditSummary().isBlank()) {
            rows += 1;
        }
        return rows;
    }

    private static int scenarioMutationBytes(TransitionPlan plan) {
        int total = 0;
        for (ScenarioDataMutation m : plan.scenarioDataMutations()) {
            total += payloadUtf8Bytes(m.payload());
        }
        return total;
    }

    private static int transitionPlanBytes(TransitionPlan plan, JsonMapper objectMapper) {
        try {
            String json = objectMapper.writeValueAsString(plan);
            return json.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw new ApplicationException("INTERNAL_ERROR", "transition plan serialization failed");
        }
    }

    private static int payloadUtf8Bytes(ScenarioMutationPayload payload) {
        if (payload == null) {
            return 2;
        }
        if (payload instanceof JsonPayload jp) {
            return utf8Map(jp.fields());
        }
        return 2;
    }

    private static int utf8Map(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return 2;
        }
        int total = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            total += Utf8LimitUtils.utf8ByteLength(e.getKey());
            total += Utf8LimitUtils.utf8ByteLength(String.valueOf(e.getValue()));
        }
        return total;
    }

    private record PlannedSignalIntentCheck(int payloadBytes) {}

    private static java.util.List<PlannedSignalIntentCheck> plannedSignalIntents(TransitionPlan plan) {
        return plan.plannedSignalIntents().stream()
                .map(si -> new PlannedSignalIntentCheck(payloadUtf8Bytes(si.payload())))
                .toList();
    }

    private static ApplicationException limit(String detail) {
        return new ApplicationException("INVALID_REQUEST", detail);
    }
}
