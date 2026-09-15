package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;

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
