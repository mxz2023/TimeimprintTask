package cn.net.mxz.timeimprint.task.service.scenario.basic.recurringtodo.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Owner test: freeze RecurringTodoScenarioExtension public method surface for P02 refactor safety. */
class RecurringTodoScenarioExtensionTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(RecurringTodoScenarioExtension.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("buildActionIntent/10",
                "descriptor/0",
                "expandDefaults/1",
                "planInitialDefinition/1",
                "processSignal/1",
                "registrationKey/0",
                "replaceSnapshotMutation/1",
                "validateDefinitionConfig/1",
                "validateScenarioState/2"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void processSignalWritesInitialAndChaseText() {
        List<String> lines = notificationLines("提交周报", "整理本周工作");
        assertEquals("INITIAL|提交周报|整理本周工作", lines.get(0));
        assertTrue(lines.size() > 1);
        assertTrue(lines.stream().skip(1).allMatch(line -> line.equals("CHASE|催办：提交周报|整理本周工作")));
    }

    @Test
    void processSignalUsesEmptyBodyWhenDescriptionMissing() {
        List<String> lines = notificationLines("提交周报", null);
        assertTrue(lines.stream().allMatch(line -> line.endsWith("|")));
        assertEquals("INITIAL|提交周报|", lines.get(0));
    }

    private static List<String> notificationLines(String title, String description) {
        Instant due = Instant.parse("2026-10-02T01:00:00Z");
        var extension = new RecurringTodoScenarioExtension(JsonMapper.builder().build());
        var definition = new TaskDefinitionSnapshot(
                3L, "local", "recurring_todo", 1, title, description, "{}",
                ControlState.ACTIVE, 1L, 1L, due, due);
        var instance = new TaskInstanceSnapshot(
                7L, 3L, 1L, 1L, 1L, "occ", due, due,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}",
                title, description, 1L, null);
        var result = extension.processSignal(new SignalProcessContext(
                definition, instance, 9L, "calendar", "due", 1, new JsonPayload(Map.of())));
        var applied = assertInstanceOf(HandlerResult.Applied.class, result);
        return applied.plan().actionJobIntents().stream()
                .map(intent -> {
                    var payload = (JsonPayload) intent.payload();
                    return payload.fields().get("purpose") + "|"
                            + payload.fields().get("title") + "|"
                            + payload.fields().get("body");
                })
                .toList();
    }
}
