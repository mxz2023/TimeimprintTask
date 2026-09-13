package cn.net.mxz.timeimprint.task.service.capability.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Daily;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.EveryNDays;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Monthly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Once;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Occurrence;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Weekly;
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
    void weeklyAndEveryNDaysPreview() {
        Weekly weekly = new Weekly(
                LocalDate.of(2026, 9, 7), // Monday
                5, // Friday
                LocalTime.of(9, 0, 0),
                MxzCalendarOccurrenceCalculator.BUSINESS_ZONE);
        Instant after = Instant.parse("2026-09-07T00:00:00Z");
        List<Occurrence> fridays = MxzCalendarOccurrenceCalculator.preview(weekly, after, 2);
        assertEquals(2, fridays.size());
        assertEquals("20260911T010000Z", fridays.get(0).occurrenceKey());
        assertEquals("20260918T010000Z", fridays.get(1).occurrenceKey());

        EveryNDays every = new EveryNDays(
                LocalDate.of(2026, 9, 1),
                3,
                LocalTime.of(9, 0, 0),
                MxzCalendarOccurrenceCalculator.BUSINESS_ZONE);
        List<Occurrence> everyList = MxzCalendarOccurrenceCalculator.preview(every, after, 3);
        assertEquals(3, everyList.size());
        assertEquals("20260907T010000Z", everyList.get(0).occurrenceKey());
        assertEquals("20260910T010000Z", everyList.get(1).occurrenceKey());
        assertEquals("20260913T010000Z", everyList.get(2).occurrenceKey());
    }
}
