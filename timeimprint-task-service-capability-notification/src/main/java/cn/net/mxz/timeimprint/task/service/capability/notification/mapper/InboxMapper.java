package cn.net.mxz.timeimprint.task.service.capability.notification.mapper;

import cn.net.mxz.timeimprint.task.service.capability.notification.row.InboxRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InboxMapper {

    void insert(InboxRow row);

    InboxRow selectById(@Param("inboxId") long inboxId,
                        @Param("tenantId") String tenantId,
                        @Param("recipientType") String recipientType,
                        @Param("recipientId") String recipientId);

    List<InboxRow> selectByRecipient(@Param("tenantId") String tenantId,
                                     @Param("recipientType") String recipientType,
                                     @Param("recipientId") String recipientId,
                                     @Param("unreadOnly") boolean unreadOnly,
                                     @Param("limit") int limit,
                                     @Param("cursor") String cursor);

    long countUnread(@Param("tenantId") String tenantId,
                     @Param("recipientType") String recipientType,
                     @Param("recipientId") String recipientId);

    int markRead(@Param("inboxId") long inboxId,
                 @Param("tenantId") String tenantId,
                 @Param("recipientType") String recipientType,
                 @Param("recipientId") String recipientId,
                 @Param("readAt") LocalDateTime readAt);
}
