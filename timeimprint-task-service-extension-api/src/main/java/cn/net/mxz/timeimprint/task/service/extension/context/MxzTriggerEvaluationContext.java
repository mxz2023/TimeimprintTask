package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;

/** 触发器评估输入（不直接改变任务业务状态）。 */
public record MxzTriggerEvaluationContext(
        MxzTaskDefinitionSnapshot definitionSnapshot,
        String bindingKey,
        int configSchemaVersion,
        ScenarioMutationPayload bindingConfig) {}
