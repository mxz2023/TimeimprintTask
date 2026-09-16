package cn.net.mxz.timeimprint.task.service.extension.trigger.context;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;

/** 触发器评估输入（不直接改变任务业务状态）。 */
public record TriggerEvaluationContext(
        TaskDefinitionSnapshot definitionSnapshot,
        String bindingKey,
        int configSchemaVersion,
        ScenarioMutationPayload bindingConfig) {}
