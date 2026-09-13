package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;

/** TransitionPlan 原子提交输入（校验通过后进入持久化）。 */
public record MxzTransitionCommitRequest(
        TransitionPlan plan,
        long definitionId,
        Long instanceId,
        String sourceType,
        String sourceKey,
        String instanceSnapshotJson) {

    public MxzTransitionCommitRequest(
            TransitionPlan plan, long definitionId, Long instanceId, String sourceType, String sourceKey) {
        this(plan, definitionId, instanceId, sourceType, sourceKey, null);
    }
}
