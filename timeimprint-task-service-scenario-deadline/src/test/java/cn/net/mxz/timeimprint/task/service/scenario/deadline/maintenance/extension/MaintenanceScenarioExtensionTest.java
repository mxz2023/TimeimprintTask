package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaintenanceScenarioExtensionTest {

    @Test
    void dueSignalRemindsWithoutClosingTheInstance() {
        Instant occurrence = Instant.parse("2026-10-14T01:00:00Z");
        var applied = assertInstanceOf(HandlerResult.Applied.class, new MaintenanceScenarioExtension().processSignal(
                new SignalProcessContext(definition(occurrence), instance(occurrence, "PLANNED"), 1L,
                        "calendar", "primary", 1, new JsonPayload(Map.of()))));
        assertEquals(LifecycleCategory.WAITING, applied.plan().instanceStateTransition().toLifecycleCategory());
        assertEquals("REMINDED", applied.plan().instanceStateTransition().toScenarioState());
        assertEquals("in_app_notification", applied.plan().actionJobIntents().getFirst().handlerKey());
    }

    @Test
    void completeAdvancesFromTheCompletionDate() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var applied = assertInstanceOf(HandlerResult.Applied.class, MaintenanceScenarioExtension.complete(
                definition(now), instance(now, "PLANNED"), now, Map.of()));
        assertEquals("COMPLETED", applied.plan().instanceStateTransition().toScenarioState());
        assertEquals("2026-10-17", String.valueOf(
                ((JsonPayload) applied.plan().scenarioDataMutations().getFirst().payload()).fields().get("nextDueDate")));
    }

    @Test
    void secondCompleteDoesNotAdvance() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var again = MaintenanceScenarioExtension.complete(
                definition(now), instance(now, "COMPLETED"), now, Map.of());
        assertInstanceOf(HandlerResult.NoChange.class, again);
    }

    private static TaskDefinitionSnapshot definition(Instant at) {
        String config = "{\"subjectName\":\"滤芯\",\"intervalDays\":3,\"nextDueDate\":\"2026-10-14\",\"localTime\":\"09:00:00\"}";
        return new TaskDefinitionSnapshot(4L, "local", "maintenance", 1, "换滤芯", "", config,
                ControlState.ACTIVE, 1L, 1L, at, at);
    }

    private static TaskInstanceSnapshot instance(Instant at, String state) {
        return new TaskInstanceSnapshot(8L, 4L, 1L, 1L, 1L, "occ", at, at,
                "COMPLETED".equals(state) ? LifecycleCategory.TERMINAL : LifecycleCategory.WAITING,
                state, 1, "{}", "换滤芯", "", 1L, null);
    }
}
