package cn.net.mxz.timeimprint.task.service.extension.command.spi;

import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;

/** 处理单一强类型业务命令；不处理 Signal。 */
public interface TaskCommandHandler {

    TaskCommandHandlerKey registrationKey();

    HandlerResult handle(CommandExecutionContext context);
}
