package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Owner test for TransitionCommitSupport. */
class TransitionCommitSupportTest {

    private final TransitionCommitSupport support = new TransitionCommitSupport(new ObjectMapper());

    @Test
    void encodesMapsAndInstants() {
        assertEquals("{}", support.toJsonFromMap(Map.of()));
        assertEquals("\"x\"", support.jsonStringEscape("x"));
        Instant instant = Instant.parse("2026-09-16T10:30:00Z");
        assertEquals(LocalDateTime.ofInstant(instant, ZoneOffset.UTC), support.toLocal(instant));
        assertNull(support.toLocal(null));
        assertEquals("v", support.toString("v", "d"));
        assertEquals("d", support.toString(null, "d"));
    }
}
