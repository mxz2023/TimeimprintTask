package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.policy.PolicyPhase;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;

/** Policy 评估输入（不得携带 HTTP DTO 或未解码 Map）。 */
public record MxzPolicyEvaluationContext(
        PolicyPhase phase,
        MxzTaskDefinitionSnapshot definitionSnapshot,
        MxzTaskInstanceSnapshot instanceSnapshot,
        String commandKey,
        Long actionJobId) {}
