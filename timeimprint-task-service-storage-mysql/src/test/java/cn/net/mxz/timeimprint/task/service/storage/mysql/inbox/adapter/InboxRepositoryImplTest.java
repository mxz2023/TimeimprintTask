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
        assertEquals(List.of("countUnread/3",
                "findById/1",
                "findByIdForRecipient/4",
                "listForRecipient/5",
                "markReadIfUnread/2",
                "maxInboxId/3"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void maxInboxIdQueriesRecipientWatermark() {
        CapturingJdbc jdbc = new CapturingJdbc();
        InboxRepositoryImpl repository = new InboxRepositoryImpl(jdbc);
        assertNull(repository.maxInboxId("tenant", "USER", "actor"));
        assertTrue(jdbc.sql.contains("MAX(inbox_id)"), jdbc.sql);
        assertTrue(jdbc.sql.contains("tenant_id = ?"), jdbc.sql);
        assertEquals(List.of("tenant", "USER", "actor"), jdbc.args);
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
    }
}
