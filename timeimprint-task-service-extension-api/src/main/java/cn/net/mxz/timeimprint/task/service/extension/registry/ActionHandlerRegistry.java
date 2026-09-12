package cn.net.mxz.timeimprint.task.service.extension.registry;

import cn.net.mxz.timeimprint.task.service.extension.spi.ActionHandler;
import java.util.Optional;

public interface ActionHandlerRegistry {

    Optional<ActionHandler> find(ActionHandlerKey key);

    ActionHandler require(ActionHandlerKey key);
}
