package cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.command;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.scenario.deadline.maintenance.extension.MaintenanceScenarioExtension;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 人工完成当前保养轮次，并按完成日重算下一次。 */
@Component
public class MaintenanceCompleteHandler implements TaskCommandHandler {

    @Override
    public TaskCommandHandlerKey registrationKey() {
        return new TaskCommandHandlerKey(
                MaintenanceScenarioExtension.SCENARIO_KEY, CommandScope.INSTANCE, "complete", 1);
    }

    @Override
    public HandlerResult handle(CommandExecutionContext context) {
        Map<String, Object> payload = context.commandPayload() instanceof JsonPayload json
                ? json.fields()
                : Map.of();
        return MaintenanceScenarioExtension.complete(
                context.definitionSnapshot(),
                context.instanceSnapshot(),
                context.nowUtc(),
                payload);
    }
}
