package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;

/** Policy 评估输入（不得携带 HTTP DTO 或未解码 Map）。 */
public record PolicyEvaluationContext(
        PolicyPhase phase,
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        String commandKey,
        Long actionJobId) {}
