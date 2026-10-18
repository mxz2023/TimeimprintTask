package cn.net.mxz.timeimprint.task.service.storage.mysql.inbox.adapter;

import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository.SoftDeleteOutcome;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InboxRepositoryImpl implements InboxRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<InboxRecord> RM = (rs, i) -> new InboxRecord(
            rs.getLong("inbox_id"),
            rs.getString("tenant_id"),
            rs.getLong("notification_id"),
            rs.getLong("action_job_id"),
            rs.getLong("definition_id"),
            rs.getLong("instance_id"),
            rs.getString("scenario_key"),
            rs.getString("purpose"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("recipient_type"),
            rs.getString("recipient_id"),
            rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
            rs.getTimestamp("created_at").toInstant());

    public InboxRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<InboxRecord> listForRecipient(
            String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit) {
        String sql =
                """
                SELECT i.inbox_id, i.tenant_id, i.notification_id, i.action_job_id, i.definition_id, i.instance_id,
                       d.scenario_key, n.purpose, n.title, n.body, i.recipient_type, i.recipient_id,
                       i.read_at, i.created_at
                FROM tt_inbox i
                JOIN tt_notification n ON n.notification_id = i.notification_id
                JOIN tt_task_definition d ON d.definition_id = i.definition_id
                WHERE i.tenant_id = ? AND i.recipient_type = ? AND i.recipient_id = ?
                """
                        + (unreadOnly ? " AND i.read_at IS NULL" : "")
                        + " AND i.deleted_at IS NULL ORDER BY i.created_at DESC, i.inbox_id DESC LIMIT ?";
        return jdbc.query(sql, RM, tenantId, recipientType, recipientId, limit);
    }

    @Override
    public Optional<InboxRecord> findById(long inboxId) {
        var list = jdbc.query(
                """
                SELECT i.inbox_id, i.tenant_id, i.notification_id, i.action_job_id, i.definition_id, i.instance_id,
                       d.scenario_key, n.purpose, n.title, n.body, i.recipient_type, i.recipient_id,
                       i.read_at, i.created_at
                FROM tt_inbox i
                JOIN tt_notification n ON n.notification_id = i.notification_id
                JOIN tt_task_definition d ON d.definition_id = i.definition_id
                WHERE i.inbox_id = ? AND i.deleted_at IS NULL
                """,
                RM,
                inboxId);
        return list.stream().findFirst();
    }

    @Override
    public Optional<InboxRecord> findByIdForRecipient(
            long inboxId, String tenantId, String recipientType, String recipientId) {
        return findById(inboxId).filter(r ->
                r.tenantId().equals(tenantId)
                        && r.recipientType().equals(recipientType)
                        && r.recipientId().equals(recipientId));
    }

    @Override
    public int countUnread(String tenantId, String recipientType, String recipientId) {
        Integer c = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_inbox
                WHERE tenant_id = ? AND recipient_type = ? AND recipient_id = ?
                  AND read_at IS NULL AND deleted_at IS NULL
                """,
                Integer.class,
                tenantId,
                recipientType,
                recipientId);
        return c == null ? 0 : c;
    }

    @Override
    public Long maxInboxId(String tenantId, String recipientType, String recipientId) {
        return jdbc.queryForObject(
                """
                SELECT MAX(inbox_id) FROM tt_inbox
                WHERE tenant_id = ? AND recipient_type = ? AND recipient_id = ? AND deleted_at IS NULL
                """,
                Long.class,
                tenantId,
                recipientType,
                recipientId);
    }

    @Override
    public boolean markReadIfUnread(long inboxId, Instant readAt) {
        int n = jdbc.update(
                "UPDATE tt_inbox SET read_at = ? WHERE inbox_id = ? AND read_at IS NULL AND deleted_at IS NULL",
                Timestamp.from(readAt),
                inboxId);
        return n == 1;
    }

    @Override
    public Optional<SoftDeleteOutcome> softDelete(
            long inboxId, String tenantId, String recipientType, String recipientId, Instant deletedAt) {
        int updated = jdbc.update(
                """
                UPDATE tt_inbox SET deleted_at = ?
                WHERE inbox_id = ? AND tenant_id = ? AND recipient_type = ? AND recipient_id = ?
                  AND deleted_at IS NULL
                """,
                Timestamp.from(deletedAt),
                inboxId,
                tenantId,
                recipientType,
                recipientId);
        if (updated == 1) {
            return Optional.of(new SoftDeleteOutcome(deletedAt, true));
        }
        List<Instant> existing = jdbc.query(
                """
                SELECT deleted_at FROM tt_inbox
                WHERE inbox_id = ? AND tenant_id = ? AND recipient_type = ? AND recipient_id = ?
                """,
                (rs, row) -> rs.getTimestamp("deleted_at") == null
                        ? null
                        : rs.getTimestamp("deleted_at").toInstant(),
                inboxId,
                tenantId,
                recipientType,
                recipientId);
        if (existing.isEmpty() || existing.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(new SoftDeleteOutcome(existing.getFirst(), false));
    }

    @Override
    public int clearRead(String tenantId, String recipientType, String recipientId, Instant deletedAt) {
        return jdbc.update(
                """
                UPDATE tt_inbox SET deleted_at = ?
                WHERE tenant_id = ? AND recipient_type = ? AND recipient_id = ?
                  AND read_at IS NOT NULL AND deleted_at IS NULL
                """,
                Timestamp.from(deletedAt),
                tenantId,
                recipientType,
                recipientId);
    }

    @Override
    public int countUnreadByInstance(long instanceId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_inbox
                WHERE instance_id = ? AND read_at IS NULL AND deleted_at IS NULL
                """,
                Integer.class,
                instanceId);
        return count == null ? 0 : count;
    }
}
