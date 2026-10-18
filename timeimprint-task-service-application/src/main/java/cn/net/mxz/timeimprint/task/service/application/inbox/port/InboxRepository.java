package cn.net.mxz.timeimprint.task.service.application.inbox.port;

import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InboxRepository {

    /** 本次软删结果。newlyDeleted 为 false 时 deletedAt 是第一次写入的时间。 */
    record SoftDeleteOutcome(Instant deletedAt, boolean newlyDeleted) {}

    List<InboxRecord> listForRecipient(
            String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit);

    Optional<InboxRecord> findById(long inboxId);

    Optional<InboxRecord> findByIdForRecipient(
            long inboxId, String tenantId, String recipientType, String recipientId);

    int countUnread(String tenantId, String recipientType, String recipientId);

    /** 该接收人最大 inboxId。没有收件时返回 null。 */
    Long maxInboxId(String tenantId, String recipientType, String recipientId);

    boolean markReadIfUnread(long inboxId, java.time.Instant readAt);

    /**
     * 软删当前接收人的一行。行不存在或不属于该接收人时为空。
     * 已经软删时返回首次时间，且 newlyDeleted 为 false。
     */
    Optional<SoftDeleteOutcome> softDelete(
            long inboxId, String tenantId, String recipientType, String recipientId, Instant deletedAt);

    /** 软删当前接收人已读且尚未删除的行，返回新写入 deleted_at 的行数。 */
    int clearRead(String tenantId, String recipientType, String recipientId, Instant deletedAt);

    /** 该实例上未读且尚未软删的收件行数，含全部接收人。 */
    int countUnreadByInstance(long instanceId);
}
