package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaintenanceCompleteHandlerTest {

    @Test
    void emptyPayloadCompletesTheOpenRound() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        String config = "{\"subjectName\":\"滤芯\",\"intervalDays\":3,\"nextDueDate\":\"2026-10-14\",\"localTime\":\"09:00:00\"}";
        var definition = new TaskDefinitionSnapshot(4L, "local", "maintenance", 1, "换滤芯", "", config,
                ControlState.ACTIVE, 1L, 2L, now, now);
        var instance = new TaskInstanceSnapshot(8L, 4L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.WAITING, "REMINDED", 1, "{}", "换滤芯", "", 3L, null);
        var ctx = new CommandExecutionContext(
                CommandScope.INSTANCE, "complete", 1, "req-1", definition, instance,
                new JsonPayload(Map.of()), now, List.of());
        var applied = assertInstanceOf(HandlerResult.Applied.class, new MaintenanceCompleteHandler().handle(ctx));
        assertEquals("COMPLETED", applied.plan().instanceStateTransition().toScenarioState());
        assertEquals(LocalDateCheck.next(), String.valueOf(
                ((JsonPayload) applied.plan().triggerBindingChanges().getFirst().configPayload()).fields()
                        .get("scenarioConfig") instanceof Map<?, ?> configMap ? configMap.get("nextDueDate") : ""));
    }

    private static final class LocalDateCheck {
        static String next() {
            return "2026-10-17";
        }
    }
}
