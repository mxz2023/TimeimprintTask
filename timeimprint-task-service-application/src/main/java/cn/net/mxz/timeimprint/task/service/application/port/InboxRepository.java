package cn.net.mxz.timeimprint.task.service.application.port;

import cn.net.mxz.timeimprint.task.service.application.model.MxzInboxRecord;
import java.util.List;
import java.util.Optional;

public interface InboxRepository {

    List<MxzInboxRecord> listForRecipient(
            String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit);

    Optional<MxzInboxRecord> findById(long inboxId);

    Optional<MxzInboxRecord> findByIdForRecipient(
            long inboxId, String tenantId, String recipientType, String recipientId);

    int countUnread(String tenantId, String recipientType, String recipientId);

    boolean markReadIfUnread(long inboxId, java.time.Instant readAt);
}
