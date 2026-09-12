package cn.net.mxz.timeimprint.task.service.capability.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Daily;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Monthly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Once;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Occurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MxzCalendarOccurrenceCalculatorTest {

    @Test
    void onceAfterExclusive() {
        Once once = new Once(LocalDate.of(2026, 9, 15), LocalTime.of(9, 0, 0), MxzCalendarOccurrenceCalculator.BUSINESS_ZONE);
        Instant after = Instant.parse("2026-09-15T00:00:00Z");
        List<Occurrence> list = MxzCalendarOccurrenceCalculator.preview(once, after, 10);
        assertEquals(1, list.size());
        assertEquals("20260915T010000Z", list.get(0).occurrenceKey());
    }

    @Test
    void monthlyClampsToMonthEnd() {
        Monthly monthly = new Monthly(
                LocalDate.of(2026, 1, 1), 31, LocalTime.of(8, 0, 0), MxzCalendarOccurrenceCalculator.BUSINESS_ZONE);
        Instant after = Instant.parse("2026-01-01T00:00:00Z");
        List<Occurrence> list = MxzCalendarOccurrenceCalculator.preview(monthly, after, 3);
        assertEquals(3, list.size());
        assertTrue(list.get(1).occurrenceKey().startsWith("20260228"));
    }

    @Test
    void dailyStrictlyAfter() {
        Daily daily = new Daily(LocalDate.of(2026, 9, 10), LocalTime.of(10, 0, 0), MxzCalendarOccurrenceCalculator.BUSINESS_ZONE);
        Instant after = Instant.parse("2026-09-12T02:00:00Z"); // 10:00 Asia/Shanghai
        List<Occurrence> list = MxzCalendarOccurrenceCalculator.preview(daily, after, 2);
        assertEquals(2, list.size());
        assertEquals("20260913T020000Z", list.get(0).occurrenceKey());
    }
}
