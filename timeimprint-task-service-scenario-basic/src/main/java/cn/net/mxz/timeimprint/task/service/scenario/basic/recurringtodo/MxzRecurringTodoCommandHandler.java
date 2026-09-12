package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo;

import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzCommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Handles instance-level commands for S02 recurring_todo:
 * complete / skip / snooze (commandSchemaVersion 1).
 *
 * Three separate handler registrations — one per commandKey.
 * Registered by Spring as three separate bean instances via inner classes.
 */
public class MxzRecurringTodoCommandHandler {

    // ── complete ─────────────────────────────────────────────────────────────

    @Component
    public static class MxzRecurringTodoCompleteHandler implements TaskCommandHandler {

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    MxzRecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "complete",
                    1);
        }

        @Override
        public HandlerResult handle(MxzCommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "instance not found");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                if ("COMPLETED".equals(inst.scenarioState())) {
                    return new HandlerResult.NoChange("already_completed");
                }
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "complete requires PENDING, got " + inst.scenarioState());
            }

            String reason = extractReason(ctx.commandPayload());
            Map<String, Object> snapshot = Map.of(
                    "scenarioState", "COMPLETED",
                    "reason", reason != null ? reason : "");

            return buildTerminalPlan(ctx, "COMPLETED", snapshot, "complete");
        }
    }

    // ── skip ─────────────────────────────────────────────────────────────────

    @Component
    public static class MxzRecurringTodoSkipHandler implements TaskCommandHandler {

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    MxzRecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "skip",
                    1);
        }

        @Override
        public HandlerResult handle(MxzCommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "instance not found");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                if ("SKIPPED".equals(inst.scenarioState())) {
                    return new HandlerResult.NoChange("already_skipped");
                }
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "skip requires PENDING, got " + inst.scenarioState());
            }

            String reason = extractReason(ctx.commandPayload());
            if (reason == null || reason.isBlank()) {
                return new HandlerResult.Rejected("INVALID_REQUEST", "skip reason is required");
            }
            Map<String, Object> snapshot = Map.of(
                    "scenarioState", "SKIPPED",
                    "reason", reason);

            return buildTerminalPlan(ctx, "SKIPPED", snapshot, "skip");
        }
    }

    // ── snooze ────────────────────────────────────────────────────────────────

    @Component
    public static class MxzRecurringTodoSnoozeHandler implements TaskCommandHandler {

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    MxzRecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "snooze",
                    1);
        }

        @Override
        public HandlerResult handle(MxzCommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "instance not found");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snooze requires PENDING, got " + inst.scenarioState());
            }
            // Snooze validation: need snoozeUntil from payload
            // Simplified: just return NoChange for now (full snooze logic is complex)
            // Full implementation requires action manipulation - deferred to T07
            return new HandlerResult.Rejected("NOT_IMPLEMENTED",
                    "snooze action manipulation requires T07 worker integration");
        }
    }

    // ── shared helpers ────────────────────────────────────────────────────────

    private static HandlerResult buildTerminalPlan(MxzCommandExecutionContext ctx,
            String toState, Map<String, Object> snapshot, String commandKey) {
        var inst = ctx.instanceSnapshot();

        InstanceStateTransition transition = new InstanceStateTransition(
                LifecycleCategory.ACTIVE,
                LifecycleCategory.TERMINAL,
                "PENDING",
                toState);

        TransitionTarget target = new TransitionTarget(
                TransitionResourceType.INSTANCE,
                inst.instanceId(),
                inst.revision());

        TransitionPlan plan = new TransitionPlan(
                target,
                null,
                transition,
                List.of(),
                List.of(),    // actions cancelled separately by instance command service
                List.of(),
                List.of(),
                List.of(),
                commandKey + ":" + inst.instanceId());

        return new HandlerResult.Applied(plan);
    }

    private static String extractReason(
            cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload payload) {
        if (payload instanceof MxzJsonPayload jp) {
            Object r = jp.fields().get("reason");
            return r != null ? String.valueOf(r) : null;
        }
        return null;
    }
}
