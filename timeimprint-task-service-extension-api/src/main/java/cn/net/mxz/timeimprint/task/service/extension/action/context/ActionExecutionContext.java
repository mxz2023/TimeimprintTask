package cn.net.mxz.timeimprint.task.service.extension.action.context;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;

/** ActionHandler 执行输入。 */
public record ActionExecutionContext(
        long actionJobId,
        long definitionId,
        Long instanceId,
        long transitionId,
        String actionKey,
        int actionSchemaVersion,
        ScenarioMutationPayload payload,
        String executionToken) {}
