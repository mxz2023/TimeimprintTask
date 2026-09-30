package cn.net.mxz.timeimprint.task.service.capability.notification.notification.mapper;

import cn.net.mxz.timeimprint.task.service.capability.notification.notification.row.NotificationRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper {

    void insert(NotificationRow row);
}
