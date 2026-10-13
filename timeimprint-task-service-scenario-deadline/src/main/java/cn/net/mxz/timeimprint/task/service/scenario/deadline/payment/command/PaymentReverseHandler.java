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

/** 冲正后已付归零，并在定义已退役时恢复。 */
@Component
public class PaymentReverseHandler implements TaskCommandHandler {

    @Override
    public TaskCommandHandlerKey registrationKey() {
        return new TaskCommandHandlerKey(PaymentScenarioExtension.SCENARIO_KEY, CommandScope.INSTANCE, "reverse", 1);
    }

    @Override
    public HandlerResult handle(CommandExecutionContext context) {
        if (!(context.commandPayload() instanceof JsonPayload json) || !json.fields().isEmpty()) {
            return new HandlerResult.Rejected("INVALID_REQUEST", "reverse payload must be empty");
        }
        return PaymentScenarioExtension.controlPlan(
                context.definitionSnapshot().controlState(),
                ControlState.ACTIVE,
                context.instanceSnapshot().instanceId(),
                context.instanceSnapshot().revision(),
                Map.of("action", "reverse", "requestId", context.requestId()));
    }
}
