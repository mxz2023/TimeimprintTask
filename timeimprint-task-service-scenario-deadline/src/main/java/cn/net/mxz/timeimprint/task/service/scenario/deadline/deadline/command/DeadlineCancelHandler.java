package cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.command;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.deadline.extension.DeadlineScenarioExtension;
import org.springframework.stereotype.Component;

/** 取消到期跟踪并退役定义。 */
@Component
public class DeadlineCancelHandler implements TaskCommandHandler {

    @Override
    public TaskCommandHandlerKey registrationKey() {
        return new TaskCommandHandlerKey(DeadlineScenarioExtension.SCENARIO_KEY, CommandScope.INSTANCE, "cancel", 1);
    }

    @Override
    public HandlerResult handle(CommandExecutionContext context) {
        if (!(context.commandPayload() instanceof JsonPayload json) || !json.fields().isEmpty()) {
            return new HandlerResult.Rejected("INVALID_REQUEST", "cancel payload must be empty");
        }
        if (!"PLANNED".equals(context.instanceSnapshot().scenarioState())) {
            return new HandlerResult.NoChange("not_planned");
        }
        return DeadlineScenarioExtension.cancel(
                context.definitionSnapshot().definitionId(),
                context.definitionSnapshot().revision(),
                context.definitionSnapshot().controlState(),
                context.instanceSnapshot().instanceId(),
                context.instanceSnapshot().revision());
    }
}
