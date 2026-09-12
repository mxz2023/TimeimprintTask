package cn.net.mxz.timeimprint.task.service.application.port;

/** TransitionPlan 提交结果摘要。 */
public record MxzTransitionCommitResult(
        long transitionId, long toRevision, int totalAffectedRows, boolean applied) {}
