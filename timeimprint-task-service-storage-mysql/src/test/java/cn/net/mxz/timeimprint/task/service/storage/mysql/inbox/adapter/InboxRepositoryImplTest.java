package cn.net.mxz.timeimprint.task.service.storage.mysql.inbox.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Owner test: freeze InboxRepositoryImpl public method surface for P02 refactor safety. */
class InboxRepositoryImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxRepositoryImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("clearRead/4",
                "countUnread/3",
                "countUnreadByInstance/1",
                "findById/1",
                "findByIdForRecipient/4",
                "listForRecipient/5",
                "markReadIfUnread/2",
                "maxInboxId/3",
                "softDelete/5"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void maxInboxIdQueriesRecipientWatermark() {
        CapturingJdbc jdbc = new CapturingJdbc();
        InboxRepositoryImpl repository = new InboxRepositoryImpl(jdbc);
        assertNull(repository.maxInboxId("tenant", "USER", "actor"));
        assertTrue(jdbc.sql.contains("MAX(inbox_id)"), jdbc.sql);
        assertTrue(jdbc.sql.contains("deleted_at IS NULL"), jdbc.sql);
        assertTrue(jdbc.sql.contains("tenant_id = ?"), jdbc.sql);
        assertEquals(List.of("tenant", "USER", "actor"), jdbc.args);
    }

    @Test
    void visibleQueriesAndSoftDeleteKeepTheRow() {
        CapturingJdbc jdbc = new CapturingJdbc();
        InboxRepositoryImpl repository = new InboxRepositoryImpl(jdbc);
        assertEquals(0, repository.countUnread("tenant", "USER", "actor"));
        assertTrue(jdbc.sql.contains("deleted_at IS NULL"), jdbc.sql);
        assertFalse(jdbc.sql.contains("DELETE "), jdbc.sql);
        assertEquals(0, repository.countUnreadByInstance(4L));
        assertTrue(jdbc.sql.contains("instance_id = ?"), jdbc.sql);
        assertTrue(jdbc.sql.contains("deleted_at IS NULL"), jdbc.sql);
        repository.listForRecipient("tenant", "USER", "actor", false, 20);
        assertTrue(jdbc.sql.contains("deleted_at IS NULL"), jdbc.sql);
        assertFalse(jdbc.sql.contains("DELETE "), jdbc.sql);
        assertTrue(repository.softDelete(8L, "tenant", "USER", "actor", java.time.Instant.parse("2026-10-18T10:54:00Z"))
                .orElseThrow()
                .newlyDeleted());
        assertTrue(jdbc.sql.contains("UPDATE tt_inbox"), jdbc.sql);
        assertTrue(jdbc.sql.contains("SET deleted_at"), jdbc.sql);
        assertFalse(jdbc.sql.contains("DELETE "), jdbc.sql);
        assertEquals(1, repository.clearRead("tenant", "USER", "actor", java.time.Instant.parse("2026-10-18T10:54:00Z")));
        assertTrue(jdbc.sql.contains("read_at IS NOT NULL"), jdbc.sql);
        assertTrue(jdbc.sql.contains("deleted_at IS NULL"), jdbc.sql);
    }

    private static final class CapturingJdbc extends JdbcTemplate {
        private String sql;
        private List<Object> args;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            this.args = List.of(args);
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = List.of(args);
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = List.of(args);
            return List.of();
        }
    }
}
