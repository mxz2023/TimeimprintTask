package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    @Test
    void formatsMinutesAndCopiesSegmentLists() {
        TimeWindow.Segment segment = new TimeWindow.Segment(9 * 60, 24 * 60);
        assertEquals("09:00", segment.formatStart());
        assertEquals("24:00", segment.formatEnd());
        TimeWindow window = new TimeWindow(TimeWindow.Mode.ALLOW, List.of(segment), List.of());
        assertEquals(TimeWindow.Mode.ALLOW, window.mode());
        assertEquals(1, window.workdaySegments().size());
        assertEquals(0, window.nonWorkdaySegments().size());
    }
}
