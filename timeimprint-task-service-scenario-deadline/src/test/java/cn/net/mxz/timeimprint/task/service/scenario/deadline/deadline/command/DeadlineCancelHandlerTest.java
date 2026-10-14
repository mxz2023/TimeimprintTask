package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.command;

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

class DeadlineCancelHandlerTest {

    @Test
    void emptyPayloadCancelsPlannedInstance() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(4L, "local", "deadline", 1, "合同", "", "{}",
                ControlState.ACTIVE, 1L, 2L, now, now);
        var instance = new TaskInstanceSnapshot(8L, 4L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "合同", "", 3L, null);
        var ctx = new CommandExecutionContext(
                CommandScope.INSTANCE, "cancel", 1, "req-1", definition, instance, new JsonPayload(Map.of()), now, List.of());
        var applied = assertInstanceOf(HandlerResult.Applied.class, new DeadlineCancelHandler().handle(ctx));
        assertEquals("CANCELLED", applied.plan().instanceStateTransition().toScenarioState());
    }
}
