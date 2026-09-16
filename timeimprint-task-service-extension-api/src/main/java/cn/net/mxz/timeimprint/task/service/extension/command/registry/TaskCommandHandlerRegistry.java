package cn.net.mxz.timeimprint.task.service.extension.command.registry;

import cn.net.mxz.timeimprint.task.service.extension.command.spi.TaskCommandHandler;
import java.util.Optional;

public interface TaskCommandHandlerRegistry {

    Optional<TaskCommandHandler> find(TaskCommandHandlerKey key);

    TaskCommandHandler require(TaskCommandHandlerKey key);
}
