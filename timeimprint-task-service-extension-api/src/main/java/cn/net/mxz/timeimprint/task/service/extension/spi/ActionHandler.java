package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import java.util.Set;

public interface ActionHandler {

    ActionHandlerKey registrationKey();

    ActionExecutionMode executionMode();

    int timeoutSeconds();

    Set<Integer> supportedSchemaVersions();

    ActionExecutionResult execute(ActionExecutionContext context);
}
