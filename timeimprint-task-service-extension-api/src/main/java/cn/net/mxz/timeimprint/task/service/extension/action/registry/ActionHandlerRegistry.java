package cn.net.mxz.timeimprint.task.service.extension.action.registry;

import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import java.util.Optional;

public interface ActionHandlerRegistry {

    Optional<ActionHandler> find(ActionHandlerKey key);

    ActionHandler require(ActionHandlerKey key);
}
