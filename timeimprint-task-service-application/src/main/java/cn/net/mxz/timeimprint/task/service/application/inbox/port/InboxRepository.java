package cn.net.mxz.timeimprint.task.service.application.inbox.port;

import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import java.util.List;
import java.util.Optional;

public interface InboxRepository {

    List<InboxRecord> listForRecipient(
            String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit);

    Optional<InboxRecord> findById(long inboxId);

    Optional<InboxRecord> findByIdForRecipient(
            long inboxId, String tenantId, String recipientType, String recipientId);

    int countUnread(String tenantId, String recipientType, String recipientId);

    boolean markReadIfUnread(long inboxId, java.time.Instant readAt);
}
