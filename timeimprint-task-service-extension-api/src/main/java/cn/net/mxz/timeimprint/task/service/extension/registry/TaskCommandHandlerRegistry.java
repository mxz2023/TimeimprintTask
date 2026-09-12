package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.spi.TaskCommandHandler;
import java.util.Optional;

public interface TaskCommandHandlerRegistry {

    Optional<TaskCommandHandler> find(TaskCommandHandlerKey key);

    TaskCommandHandler require(TaskCommandHandlerKey key);
}
