package cn.net.mxz.timeimprint.task.service.kernel.domain.plan;

import cn.net.mxz.timeimprint.task.service.kernel.domain.revision.MxzRevisions;

/**
 * 迁移目标资源及乐观锁起始版本（Applied 计划必填）。
 */
public record TransitionTarget(TransitionResourceType resourceType, long resourceId, long fromRevision) {
    public TransitionTarget {
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        MxzRevisions.validateTransitionFrom(fromRevision);
    }
}
