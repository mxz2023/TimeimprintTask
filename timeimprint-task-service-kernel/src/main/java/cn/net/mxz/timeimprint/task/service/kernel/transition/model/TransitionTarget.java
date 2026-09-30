package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import cn.net.mxz.timeimprint.task.service.kernel.transition.revision.Revisions;

/**
 * 迁移目标资源及乐观锁起始版本（Applied 计划必填）。
 */
public record TransitionTarget(TransitionResourceType resourceType, long resourceId, long fromRevision) {
    public TransitionTarget {
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        Revisions.validateTransitionFrom(fromRevision);
    }
}
