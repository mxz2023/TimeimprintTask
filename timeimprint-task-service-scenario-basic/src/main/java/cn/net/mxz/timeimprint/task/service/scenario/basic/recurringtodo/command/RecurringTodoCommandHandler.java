package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.command;

import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionJobView;
import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.extension.RecurringTodoScenarioExtension;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;

/**
 * Handles instance-level commands for S02 recurring_todo:
 * complete / skip / snooze (commandSchemaVersion 1).
 */
public class RecurringTodoCommandHandler {

    // ── complete ─────────────────────────────────────────────────────────────

    @Component
    public static class RecurringTodoCompleteHandler implements TaskCommandHandler {

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    RecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "complete",
                    1);
        }

        @Override
        public HandlerResult handle(CommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "实例不存在");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                if ("COMPLETED".equals(inst.scenarioState())) {
                    return new HandlerResult.NoChange("already_completed");
                }
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "complete 要求场景状态为 PENDING，当前为 " + inst.scenarioState());
            }

            String reason = extractReason(ctx.commandPayload());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scenarioState", "COMPLETED");
            snapshot.put("reason", reason != null ? reason : "");

            return buildTerminalPlan(ctx, "COMPLETED", snapshot, "complete");
        }
    }

    // ── skip ─────────────────────────────────────────────────────────────────

    @Component
    public static class RecurringTodoSkipHandler implements TaskCommandHandler {

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    RecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "skip",
                    1);
        }

        @Override
        public HandlerResult handle(CommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "实例不存在");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                if ("SKIPPED".equals(inst.scenarioState())) {
                    return new HandlerResult.NoChange("already_skipped");
                }
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "skip 要求场景状态为 PENDING，当前为 " + inst.scenarioState());
            }

            String reason = extractReason(ctx.commandPayload());
            if (reason == null || reason.isBlank()) {
                return new HandlerResult.Rejected("INVALID_REQUEST", "skip 命令必须在 payload.reason 提供跳过原因");
            }
            if (reason.codePointCount(0, reason.length()) > 500) {
                return new HandlerResult.Rejected("INVALID_REQUEST", "skip 的 reason 超过 500 个 Unicode 码点上限");
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scenarioState", "SKIPPED");
            snapshot.put("reason", reason);

            return buildTerminalPlan(ctx, "SKIPPED", snapshot, "skip");
        }
    }

    // ── snooze ────────────────────────────────────────────────────────────────

    @Component
    public static class RecurringTodoSnoozeHandler implements TaskCommandHandler {

        private final ObjectMapper objectMapper;

        public RecurringTodoSnoozeHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public TaskCommandHandlerKey registrationKey() {
            return new TaskCommandHandlerKey(
                    RecurringTodoScenarioExtension.SCENARIO_KEY,
                    CommandScope.INSTANCE,
                    "snooze",
                    1);
        }

        @Override
        public HandlerResult handle(CommandExecutionContext ctx) {
            var inst = ctx.instanceSnapshot();
            var def = ctx.definitionSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "实例不存在");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snooze 要求场景状态为 PENDING，当前为 " + inst.scenarioState());
            }
            if (def.controlState() == ControlState.PAUSED || def.controlState() == ControlState.RETIRED) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "定义处于 " + def.controlState() + " 时不允许 snooze");
            }

            Instant snoozeUntil = extractSnoozeUntil(ctx.commandPayload());
            if (snoozeUntil == null) {
                return new HandlerResult.Rejected("INVALID_REQUEST", "snooze 命令必须提供未来的 snoozeUntil");
            }

            Map<String, Object> snapshot = parseSnapshot(inst.scenarioSnapshotJson());
            int snoozeCount = asInt(snapshot.get("snoozeCount"), 0);
            int maxSnooze = asInt(snapshot.get("maxSnoozeCount"),
                    RecurringTodoScenarioExtension.DEFAULT_MAX_SNOOZE_COUNT);
            int actionGeneration = asInt(snapshot.get("actionGeneration"), 1);
            Instant expiresAt = parseInstant(snapshot.get("expiresAt"), inst.dueAt());
            if (expiresAt == null) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "实例缺少 expiresAt，无法执行 snooze");
            }
            if (snoozeCount >= maxSnooze) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "已达到 maxSnoozeCount，不能再次 snooze");
            }
            Instant now = ctx.nowUtc();
            if (!snoozeUntil.isAfter(now) || !snoozeUntil.isBefore(expiresAt)) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snoozeUntil 必须晚于当前时间且严格早于 expiresAt");
            }

            List<ActionJobView> movable = ctx.actionJobs().stream()
                    .filter(a -> "READY".equals(a.status()) || "RETRY_WAIT".equals(a.status()))
                    .sorted(Comparator.comparing(ActionJobView::availableAt))
                    .toList();
            if (movable.isEmpty()) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "没有可平移的 READY/RETRY_WAIT Action，无法 snooze");
            }

            Instant anchor = movable.get(0).availableAt();
            Duration delta = Duration.between(anchor, snoozeUntil);
            List<ActionJobIntent> newActions = new ArrayList<>();
            List<String> remainingTimes = new ArrayList<>();
            int newGeneration = actionGeneration + 1;

            for (ActionJobView old : movable) {
                Instant newAvailable = old.availableAt().plus(delta);
                if (!newAvailable.isBefore(expiresAt)) {
                    return new HandlerResult.Rejected("STATE_CONFLICT",
                            "平移后的 Action 会到达或超过 expiresAt");
                }
                Map<String, Object> payload = parsePayload(old.payloadJson());
                String purpose = String.valueOf(payload.getOrDefault("purpose", "INITIAL"));
                int slotIndex = asInt(payload.get("slotIndex"), 0);
                String recipientId = String.valueOf(payload.getOrDefault("recipientId", "local-actor"));
                Instant actionExpires = old.expiresAt() != null ? old.expiresAt() : expiresAt;
                newActions.add(RecurringTodoScenarioExtension.buildActionIntent(
                        def.definitionId(),
                        inst.instanceId(),
                        purpose,
                        slotIndex,
                        newGeneration,
                        recipientId,
                        newAvailable,
                        actionExpires));
                remainingTimes.add(newAvailable.toString());
            }

            Map<String, Object> nextSnapshot = new LinkedHashMap<>(snapshot);
            nextSnapshot.put("scenarioState", "PENDING");
            nextSnapshot.put("snoozeCount", snoozeCount + 1);
            nextSnapshot.put("actionGeneration", newGeneration);
            nextSnapshot.put("lastSnoozeUntil", snoozeUntil.toString());
            nextSnapshot.put("remainingReminderAts", remainingTimes);

            InstanceStateTransition stay = new InstanceStateTransition(
                    LifecycleCategory.ACTIVE,
                    LifecycleCategory.ACTIVE,
                    "PENDING",
                    "PENDING");
            TransitionTarget target = new TransitionTarget(
                    TransitionResourceType.INSTANCE,
                    inst.instanceId(),
                    inst.revision());
            TransitionPlan plan = new TransitionPlan(
                    target,
                    null,
                    stay,
                    List.of(),
                    newActions,
                    List.of(),
                    List.of(),
                    List.of(RecurringTodoScenarioExtension.replaceSnapshotMutation(nextSnapshot)),
                    "snooze:" + inst.instanceId());
            return new HandlerResult.Applied(plan);
        }

        private Map<String, Object> parseSnapshot(String json) {
            try {
                if (json == null || json.isBlank()) {
                    return new LinkedHashMap<>();
                }
                return new LinkedHashMap<>(objectMapper.readValue(json, new TypeReference<>() {}));
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }

        private Map<String, Object> parsePayload(String json) {
            try {
                if (json == null || json.isBlank()) {
                    return Map.of();
                }
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return Map.of();
            }
        }
    }

    // ── shared helpers ────────────────────────────────────────────────────────

    private static HandlerResult buildTerminalPlan(CommandExecutionContext ctx,
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
                List.of(),
                List.of(),
                List.of(),
                List.of(RecurringTodoScenarioExtension.replaceSnapshotMutation(snapshot)),
                commandKey + ":" + inst.instanceId());

        return new HandlerResult.Applied(plan);
    }

    private static String extractReason(
            cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload payload) {
        if (payload instanceof JsonPayload jp) {
            Object r = jp.fields().get("reason");
            if (r == null) {
                r = jp.fields().get("note");
            }
            return r != null ? String.valueOf(r) : null;
        }
        return null;
    }

    private static Instant extractSnoozeUntil(
            cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload payload) {
        if (!(payload instanceof JsonPayload jp)) {
            return null;
        }
        Object v = jp.fields().get("snoozeUntil");
        if (v == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant parseInstant(Object v, Instant fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Instant.parse(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int asInt(Object v, int defaultVal) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
