package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionJobView;
import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Owner test for RecurringTodoCommandHandler holder: freezes nested command handler types.
 */
class RecurringTodoCommandHandlerTest {

    @Test
    void freezesNestedCommandHandlers() {
        List<String> nested = Arrays.stream(RecurringTodoCommandHandler.class.getDeclaredClasses())
                .filter(c -> java.lang.reflect.Modifier.isPublic(c.getModifiers()))
                .filter(c -> java.lang.reflect.Modifier.isStatic(c.getModifiers()))
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(
                List.of(
                        "RecurringTodoCompleteHandler",
                        "RecurringTodoSkipHandler",
                        "RecurringTodoSnoozeHandler"),
                nested);
        for (Class<?> type : RecurringTodoCommandHandler.class.getDeclaredClasses()) {
            if (nested.contains(type.getSimpleName())) {
                assertTrue(TaskCommandHandler.class.isAssignableFrom(type), type.getName());
            }
        }
    }

    @Test
    void snoozeCopiesExistingNotificationText() {
        Instant now = Instant.parse("2026-10-02T01:00:00Z");
        Instant expiresAt = Instant.parse("2026-10-03T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(
                3L, "local", "recurring_todo", 1, "别的标题", "别的描述", "{}",
                ControlState.ACTIVE, 1L, 1L, now, now);
        var instance = new TaskInstanceSnapshot(
                7L, 3L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.ACTIVE, "PENDING", 1,
                "{\"snoozeCount\":0,\"maxSnoozeCount\":3,\"actionGeneration\":1,\"expiresAt\":\"2026-10-03T01:00:00Z\"}",
                "别的标题", "别的描述", 2L, null);
        var chase = new ActionJobView(
                11L, "CHASE:old", "READY", now.plusSeconds(60), expiresAt,
                "{\"purpose\":\"CHASE\",\"slotIndex\":1,\"recipientId\":\"local-actor\",\"title\":\"催办：提交周报\",\"body\":\"整理本周工作\"}");
        var blank = new ActionJobView(
                12L, "INITIAL:old", "READY", now.plusSeconds(120), expiresAt,
                "{\"purpose\":\"INITIAL\",\"slotIndex\":0,\"recipientId\":\"local-actor\"}");
        var ctx = new CommandExecutionContext(
                CommandScope.INSTANCE, "snooze", 1, "req", definition, instance,
                new JsonPayload(Map.of("snoozeUntil", now.plusSeconds(180).toString())),
                now, List.of(chase, blank));
        var result = new RecurringTodoCommandHandler.RecurringTodoSnoozeHandler(JsonMapper.builder().build())
                .handle(ctx);
        var applied = assertInstanceOf(HandlerResult.Applied.class, result);
        var titles = applied.plan().actionJobIntents().stream()
                .map(intent -> {
                    var payload = (JsonPayload) intent.payload();
                    return payload.fields().get("purpose") + "|" + payload.fields().get("title") + "|"
                            + payload.fields().get("body");
                })
                .toList();
        assertEquals(List.of("CHASE|催办：提交周报|整理本周工作", "INITIAL||"), titles);
    }

    @Test
    void snoozeEmitsOneIntentPerSlotWhenSameSlotHasMultipleChannelActions() {
        Instant now = Instant.parse("2026-10-02T01:00:00Z");
        Instant expiresAt = Instant.parse("2026-10-03T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(
                3L, "local", "recurring_todo", 1, "t", "d", "{}",
                ControlState.ACTIVE, 1L, 1L, now, now);
        var instance = new TaskInstanceSnapshot(
                7L, 3L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.ACTIVE, "PENDING", 1,
                "{\"snoozeCount\":0,\"maxSnoozeCount\":3,\"actionGeneration\":1,\"expiresAt\":\"2026-10-03T01:00:00Z\"}",
                "t", "d", 2L, null);
        String initial = "{\"purpose\":\"INITIAL\",\"slotIndex\":0,\"recipientId\":\"local-actor\"}";
        String chase = "{\"purpose\":\"CHASE\",\"slotIndex\":1,\"recipientId\":\"local-actor\"}";
        var actions = List.of(
                new ActionJobView(11L, "in_app-INITIAL", "READY", now.plusSeconds(60), expiresAt, initial),
                new ActionJobView(12L, "feishu-INITIAL", "READY", now.plusSeconds(60), expiresAt, initial),
                new ActionJobView(13L, "in_app-CHASE", "READY", now.plusSeconds(120), expiresAt, chase),
                new ActionJobView(14L, "feishu-CHASE", "READY", now.plusSeconds(120), expiresAt, chase));
        var ctx = new CommandExecutionContext(
                CommandScope.INSTANCE, "snooze", 1, "req", definition, instance,
                new JsonPayload(Map.of("snoozeUntil", now.plusSeconds(600).toString())),
                now, actions);
        var applied = assertInstanceOf(
                HandlerResult.Applied.class,
                new RecurringTodoCommandHandler.RecurringTodoSnoozeHandler(JsonMapper.builder().build()).handle(ctx));
        var slots = applied.plan().actionJobIntents().stream()
                .map(intent -> ((JsonPayload) intent.payload()).fields().get("purpose") + "|"
                        + ((JsonPayload) intent.payload()).fields().get("slotIndex"))
                .toList();
        assertEquals(List.of("INITIAL|0", "CHASE|1"), slots);
    }
}
