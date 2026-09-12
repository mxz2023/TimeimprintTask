package cn.net.mxz.timeimprint.task.service.capability.notification.repo;

import cn.net.mxz.timeimprint.task.service.capability.notification.mapper.NotificationMapper;
import cn.net.mxz.timeimprint.task.service.capability.notification.port.NotificationMaterializationPort;
import cn.net.mxz.timeimprint.task.service.capability.notification.row.NotificationRow;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** Implements the port to write tt_notification within an active transaction. */
@Component
public class MxzNotificationMaterializationAdapter implements NotificationMaterializationPort {

    private final NotificationMapper notificationMapper;

    public MxzNotificationMaterializationAdapter(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public long insertNotification(String tenantId, long definitionId, long instanceId,
                                    long transitionId, String title, String body, String purpose,
                                    LocalDateTime createdAt) {
        NotificationRow row = new NotificationRow();
        row.setTenantId(tenantId);
        row.setDefinitionId(definitionId);
        row.setInstanceId(instanceId);
        row.setTransitionId(transitionId);
        row.setTitle(title);
        row.setBody(body);
        row.setPurpose(purpose);
        row.setCreatedAt(createdAt);
        notificationMapper.insert(row);
        return row.getNotificationId();
    }
}
