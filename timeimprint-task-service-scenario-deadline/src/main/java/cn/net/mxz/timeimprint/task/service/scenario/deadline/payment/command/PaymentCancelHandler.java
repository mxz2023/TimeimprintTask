package cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.command;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.payment.extension.PaymentScenarioExtension;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 取消账期并退役定义。 */
@Component
public class PaymentCancelHandler implements TaskCommandHandler {

    @Override
    public TaskCommandHandlerKey registrationKey() {
        return new TaskCommandHandlerKey(PaymentScenarioExtension.SCENARIO_KEY, CommandScope.INSTANCE, "cancel", 1);
    }

    @Override
    public HandlerResult handle(CommandExecutionContext context) {
        if (!(context.commandPayload() instanceof JsonPayload json) || !json.fields().isEmpty()) {
            return new HandlerResult.Rejected("INVALID_REQUEST", "cancel payload must be empty");
        }
        return PaymentScenarioExtension.controlPlan(
                context.definitionSnapshot().controlState(),
                ControlState.RETIRED,
                context.instanceSnapshot().instanceId(),
                context.instanceSnapshot().revision(),
                Map.of("action", "cancel", "requestId", context.requestId()));
    }
}
