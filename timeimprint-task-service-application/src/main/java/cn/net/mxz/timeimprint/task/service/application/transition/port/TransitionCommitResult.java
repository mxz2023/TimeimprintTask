package cn.net.mxz.timeimprint.task.service.application.transition.port;

/** TransitionPlan 提交结果摘要。 */
public record TransitionCommitResult(
        long transitionId, long toRevision, int totalAffectedRows, boolean applied) {}
