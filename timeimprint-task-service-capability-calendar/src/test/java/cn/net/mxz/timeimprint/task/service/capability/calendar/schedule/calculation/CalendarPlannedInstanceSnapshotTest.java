package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarPlannedInstanceSnapshotTest {

    @Test
    void hourAndMinuteSnapshotsDisallowSnooze() {
        assertTrue(CalendarPlannedInstanceSnapshot.snoozeDisallowed(Map.of("type", "EVERY_N_HOURS")));
        assertTrue(CalendarPlannedInstanceSnapshot.snoozeDisallowedType("every_n_minutes"));
        assertEquals(
                "{\"occurrenceKey\":\"k\",\"snoozeAllowed\":false}",
                CalendarPlannedInstanceSnapshot.json("k", Map.of("type", "EVERY_N_MINUTES")));
    }

    @Test
    void dailySnapshotStaysOccurrenceKeyOnly() {
        assertFalse(CalendarPlannedInstanceSnapshot.snoozeDisallowed(Map.of("type", "DAILY")));
        assertEquals(
                "{\"occurrenceKey\":\"k\"}",
                CalendarPlannedInstanceSnapshot.json("k", Map.of("type", "DAILY")));
    }
}
