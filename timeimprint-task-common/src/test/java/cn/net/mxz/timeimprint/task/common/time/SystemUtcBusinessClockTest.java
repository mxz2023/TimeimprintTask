package cn.net.mxz.timeimprint.task.common.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Owner test for SystemUtcBusinessClock. */
class SystemUtcBusinessClockTest {

    @Test
    void nowUtcSecondsTracksAndTruncates() {
        Instant fixed = Instant.parse("2026-09-16T10:30:45.123Z");
        SystemUtcBusinessClock clock = new SystemUtcBusinessClock(Clock.fixed(fixed, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-09-16T10:30:45Z"), clock.nowUtcSeconds());

        SystemUtcBusinessClock system = new SystemUtcBusinessClock();
        Instant before = Instant.now().minusSeconds(1);
        Instant value = system.nowUtcSeconds();
        Instant after = Instant.now().plusSeconds(1);
        assertTrue(!value.isBefore(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
        assertTrue(!value.isAfter(after));
        assertEquals(0, value.getNano());
        assertTrue(Duration.between(before, value).abs().toSeconds() < 5);
    }
}
