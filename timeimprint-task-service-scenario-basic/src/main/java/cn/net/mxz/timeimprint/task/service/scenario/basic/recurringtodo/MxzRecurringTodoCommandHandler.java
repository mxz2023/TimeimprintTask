package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo;

import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionJobView;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzCommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
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

/**
 * Handles instance-level commands for S02 recurring_todo:
 * complete / skip / snooze (commandSchemaVersion 1).
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
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scenarioState", "COMPLETED");
            snapshot.put("reason", reason != null ? reason : "");

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
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("scenarioState", "SKIPPED");
            snapshot.put("reason", reason);

            return buildTerminalPlan(ctx, "SKIPPED", snapshot, "skip");
        }
    }

    // ── snooze ────────────────────────────────────────────────────────────────

    @Component
    public static class MxzRecurringTodoSnoozeHandler implements TaskCommandHandler {

        private final ObjectMapper objectMapper;

        public MxzRecurringTodoSnoozeHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

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
            var def = ctx.definitionSnapshot();
            if (inst == null) {
                return new HandlerResult.Rejected("RESOURCE_NOT_FOUND", "instance not found");
            }
            if (!"PENDING".equals(inst.scenarioState())) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snooze requires PENDING, got " + inst.scenarioState());
            }
            if (def.controlState() == ControlState.PAUSED || def.controlState() == ControlState.RETIRED) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snooze not allowed when definition is " + def.controlState());
            }

            Instant snoozeUntil = extractSnoozeUntil(ctx.commandPayload());
            if (snoozeUntil == null) {
                return new HandlerResult.Rejected("INVALID_REQUEST", "snoozeUntil is required");
            }

            Map<String, Object> snapshot = parseSnapshot(inst.scenarioSnapshotJson());
            int snoozeCount = asInt(snapshot.get("snoozeCount"), 0);
            int maxSnooze = asInt(snapshot.get("maxSnoozeCount"),
                    MxzRecurringTodoScenarioExtension.DEFAULT_MAX_SNOOZE_COUNT);
            int actionGeneration = asInt(snapshot.get("actionGeneration"), 1);
            Instant expiresAt = parseInstant(snapshot.get("expiresAt"), inst.dueAt());
            if (expiresAt == null) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "instance expiresAt missing");
            }
            if (snoozeCount >= maxSnooze) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "maxSnoozeCount exceeded");
            }
            Instant now = ctx.nowUtc();
            if (!snoozeUntil.isAfter(now) || !snoozeUntil.isBefore(expiresAt)) {
                return new HandlerResult.Rejected("STATE_CONFLICT",
                        "snoozeUntil must be after now and strictly before expiresAt");
            }

            List<MxzActionJobView> movable = ctx.actionJobs().stream()
                    .filter(a -> "READY".equals(a.status()) || "RETRY_WAIT".equals(a.status()))
                    .sorted(Comparator.comparing(MxzActionJobView::availableAt))
                    .toList();
            if (movable.isEmpty()) {
                return new HandlerResult.Rejected("STATE_CONFLICT", "no movable actions");
            }

            Instant anchor = movable.get(0).availableAt();
            Duration delta = Duration.between(anchor, snoozeUntil);
            List<ActionJobIntent> newActions = new ArrayList<>();
            List<String> remainingTimes = new ArrayList<>();
            int newGeneration = actionGeneration + 1;

            for (MxzActionJobView old : movable) {
                Instant newAvailable = old.availableAt().plus(delta);
                if (!newAvailable.isBefore(expiresAt)) {
                    return new HandlerResult.Rejected("STATE_CONFLICT",
                            "shifted action would reach or pass expiresAt");
                }
                Map<String, Object> payload = parsePayload(old.payloadJson());
                String purpose = String.valueOf(payload.getOrDefault("purpose", "INITIAL"));
                int slotIndex = asInt(payload.get("slotIndex"), 0);
                String recipientId = String.valueOf(payload.getOrDefault("recipientId", "local-actor"));
                Instant actionExpires = old.expiresAt() != null ? old.expiresAt() : expiresAt;
                newActions.add(MxzRecurringTodoScenarioExtension.buildActionIntent(
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
                    List.of(MxzRecurringTodoScenarioExtension.replaceSnapshotMutation(nextSnapshot)),
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
                List.of(),
                List.of(),
                List.of(),
                List.of(MxzRecurringTodoScenarioExtension.replaceSnapshotMutation(snapshot)),
                commandKey + ":" + inst.instanceId());

        return new HandlerResult.Applied(plan);
    }

    private static String extractReason(
            cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload payload) {
        if (payload instanceof MxzJsonPayload jp) {
            Object r = jp.fields().get("reason");
            if (r == null) {
                r = jp.fields().get("note");
            }
            return r != null ? String.valueOf(r) : null;
        }
        return null;
    }

    private static Instant extractSnoozeUntil(
            cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload payload) {
        if (!(payload instanceof MxzJsonPayload jp)) {
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
