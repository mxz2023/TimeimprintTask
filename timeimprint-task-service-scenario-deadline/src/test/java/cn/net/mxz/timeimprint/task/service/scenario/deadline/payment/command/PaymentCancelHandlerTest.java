package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.command;

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

class PaymentCancelHandlerTest {

    @Test
    void cancelRetiresDefinition() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var definition = new TaskDefinitionSnapshot(5L, "local", "payment", 1, "房租", "", "{}",
                ControlState.ACTIVE, 1L, 2L, now, now);
        var instance = new TaskInstanceSnapshot(9L, 5L, 1L, 1L, 1L, "occ", now, now,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "房租", "", 1L, null);
        var ctx = new CommandExecutionContext(CommandScope.INSTANCE, "cancel", 1, "req-cancel",
                definition, instance, new JsonPayload(Map.of()), now, List.of());
        var applied = assertInstanceOf(HandlerResult.Applied.class, new PaymentCancelHandler().handle(ctx));
        assertEquals(ControlState.RETIRED, applied.plan().definitionControlTransition().toControlState());
    }
}
