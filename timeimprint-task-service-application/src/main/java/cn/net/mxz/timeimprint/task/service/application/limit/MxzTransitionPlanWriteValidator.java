package cn.net.mxz.timeimprint.task.service.application.limit;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Pre-write validation for TransitionPlan scale limits (A25/A29). */
public final class MxzTransitionPlanWriteValidator {

    private MxzTransitionPlanWriteValidator() {}

    public static void validateBeforeWrite(TransitionPlan plan, ObjectMapper objectMapper) {
        int actions = plan.actionJobIntents().size();
        if (actions > MxzPlatformLimits.MAX_ACTIONS_PER_TRANSITION) {
            throw limit("too many actions in transition");
        }
        if (actions > MxzPlatformLimits.MAX_ACTIONS_PER_WRITE_TX) {
            throw limit("too many actions in write transaction");
        }
        int mutations = plan.scenarioDataMutations().size();
        if (mutations > MxzPlatformLimits.MAX_SCENARIO_MUTATIONS) {
            throw limit("too many scenario mutations");
        }
        int mutationBytes = scenarioMutationBytes(plan);
        if (mutationBytes > MxzPlatformLimits.MAX_SCENARIO_MUTATION_BYTES) {
            throw limit("scenario mutation payload too large");
        }
        int planBytes = transitionPlanBytes(plan, objectMapper);
        if (planBytes > MxzPlatformLimits.MAX_TRANSITION_PLAN_BYTES) {
            throw limit("transition plan too large");
        }
        int rows = estimateMutatedRows(plan);
        if (rows > MxzPlatformLimits.MAX_MUTATED_ROWS_PER_WRITE_TX) {
            throw limit("too many mutated rows estimated");
        }
        for (ActionJobIntent aj : plan.actionJobIntents()) {
            int payloadBytes = payloadUtf8Bytes(aj.payload());
            if (payloadBytes > MxzPlatformLimits.MAX_JSON_VALUE_BYTES) {
                throw limit("action payload too large");
            }
        }
        for (PlannedSignalIntentCheck si : plannedSignalIntents(plan)) {
            if (si.payloadBytes() > MxzPlatformLimits.MAX_JSON_VALUE_BYTES) {
                throw limit("signal payload too large");
            }
        }
    }

    /**
     * Rough row budget aligned with {@code MxzTransitionPlanCommitterImpl} (unit-testable).
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
            if ("in_app_notification".equals(aj.handlerKey())) {
                rows += 1; // notification row
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

    private static int transitionPlanBytes(TransitionPlan plan, ObjectMapper objectMapper) {
        try {
            String json = objectMapper.writeValueAsString(plan);
            return json.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw new MxzApplicationException("INTERNAL_ERROR", "transition plan serialization failed");
        }
    }

    private static int payloadUtf8Bytes(ScenarioMutationPayload payload) {
        if (payload == null) {
            return 2;
        }
        if (payload instanceof MxzJsonPayload jp) {
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
            total += MxzUtf8LimitUtils.utf8ByteLength(e.getKey());
            total += MxzUtf8LimitUtils.utf8ByteLength(String.valueOf(e.getValue()));
        }
        return total;
    }

    private record PlannedSignalIntentCheck(int payloadBytes) {}

    private static java.util.List<PlannedSignalIntentCheck> plannedSignalIntents(TransitionPlan plan) {
        return plan.plannedSignalIntents().stream()
                .map(si -> new PlannedSignalIntentCheck(payloadUtf8Bytes(si.payload())))
                .toList();
    }

    private static MxzApplicationException limit(String detail) {
        return new MxzApplicationException("INVALID_REQUEST", detail);
    }
}
