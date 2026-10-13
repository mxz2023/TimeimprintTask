package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeadlineScenarioExtensionTest {

    @Test
    void dueDayExpiresSubject() {
        Instant occurrence = Instant.parse("2026-10-14T01:00:00Z");
        String config = "{\"subjectType\":\"CONTRACT\",\"dueDate\":\"2026-10-14\",\"localTime\":\"09:00:00\",\"offsetDays\":[0]}";
        var definition = new TaskDefinitionSnapshot(4L, "local", "deadline", 1, "合同", "", config,
                ControlState.ACTIVE, 1L, 1L, occurrence, occurrence);
        var instance = new TaskInstanceSnapshot(8L, 4L, 1L, 1L, 1L, "occ", occurrence, occurrence,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "合同", "", 1L, null);
        var applied = assertInstanceOf(HandlerResult.Applied.class, new DeadlineScenarioExtension().processSignal(
                new SignalProcessContext(definition, instance, 1L, "calendar", "primary", 1, new JsonPayload(Map.of()))));
        assertEquals("EXPIRED", applied.plan().instanceStateTransition().toScenarioState());
        assertEquals("expire", applied.plan().scenarioDataMutations().getFirst().payload().toString().contains("expire")
                ? "expire" : applied.plan().scenarioDataMutations().getFirst().payload().toString());
    }

    @Test
    void cancelRetiresActiveDefinition() {
        var applied = assertInstanceOf(HandlerResult.Applied.class,
                DeadlineScenarioExtension.cancel(4L, 2L, ControlState.ACTIVE, 8L, 3L));
        assertEquals(ControlState.RETIRED, applied.plan().definitionControlTransition().toControlState());
        assertEquals("CANCELLED", applied.plan().instanceStateTransition().toScenarioState());
    }

    @Test
    void cancelOfRetiredDefinitionDoesNotRepeatControl() {
        var applied = assertInstanceOf(HandlerResult.Applied.class,
                DeadlineScenarioExtension.cancel(4L, 2L, ControlState.RETIRED, 8L, 3L));
        assertNull(applied.plan().definitionControlTransition());
    }
}
