package cn.net.mxz.timeimprint.task.service.extension.policy.context;

import cn.net.mxz.timeimprint.task.service.extension.policy.spi.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;

/** Policy 评估输入（不得携带 HTTP DTO 或未解码 Map）。 */
public record PolicyEvaluationContext(
        PolicyPhase phase,
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        String commandKey,
        Long actionJobId) {}
