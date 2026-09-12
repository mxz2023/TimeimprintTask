package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.action.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.registry.ActionHandlerKey;
import java.util.Set;

public interface ActionHandler {

    ActionHandlerKey registrationKey();

    ActionExecutionMode executionMode();

    int timeoutSeconds();

    Set<Integer> supportedSchemaVersions();

    MxzActionExecutionResult execute(MxzActionExecutionContext context);
}
