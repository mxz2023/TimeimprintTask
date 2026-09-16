package cn.net.mxz.timeimprint.task.service.extension.action.spi;

import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import java.util.Set;

public interface ActionHandler {

    ActionHandlerKey registrationKey();

    ActionExecutionMode executionMode();

    int timeoutSeconds();

    Set<Integer> supportedSchemaVersions();

    ActionExecutionResult execute(ActionExecutionContext context);
}
