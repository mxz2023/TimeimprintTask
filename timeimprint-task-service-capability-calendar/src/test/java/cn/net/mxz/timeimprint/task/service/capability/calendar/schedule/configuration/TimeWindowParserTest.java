package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeWindowParserTest {

    @Test
    void parsesNullAndDetectsRingOverlap() {
        assertNull(TimeWindowParser.parse(null));
        TimeWindow window = TimeWindowParser.parse(Map.of(
                "mode",
                "DENY",
                "workdaySegments",
                List.of(Map.of("start", "09:00", "end", "10:00")),
                "nonWorkdaySegments",
                List.of(Map.of("start", "22:00", "end", "24:00"))));
        assertEquals(TimeWindow.Mode.DENY, window.mode());
        assertEquals(10 * 60, window.workdaySegments().get(0).endMinute());
        assertEquals(24 * 60, window.nonWorkdaySegments().get(0).endMinute());
        assertTrue(TimeWindowParser.overlaps(new TimeWindow.Segment(22 * 60, 2 * 60), new TimeWindow.Segment(1 * 60, 3 * 60)));
        assertFalse(TimeWindowParser.overlaps(new TimeWindow.Segment(9 * 60, 12 * 60), new TimeWindow.Segment(12 * 60, 13 * 60)));
    }
}
