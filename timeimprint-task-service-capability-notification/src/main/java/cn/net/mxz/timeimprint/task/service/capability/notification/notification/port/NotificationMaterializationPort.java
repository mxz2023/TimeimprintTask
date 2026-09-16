package cn.net.mxz.timeimprint.task.service.capability.notification.notification.port;

import java.time.LocalDateTime;

/**
 * Called by TransitionPlanCommitter within the same transaction to materialize
 * a notification record and its action job.
 */
public interface NotificationMaterializationPort {

    /**
     * Insert tt_notification and return the generated notification_id.
     */
    long insertNotification(String tenantId, long definitionId, long instanceId, long transitionId,
                             String title, String body, String purpose, LocalDateTime createdAt);
}
