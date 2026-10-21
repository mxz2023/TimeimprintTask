package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Occurrence;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Rule;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigException;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CnMainlandWorkdayCalendar;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.TimeWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarWindowedPreviewTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final CnMainlandWorkdayCalendar calendar = new CnMainlandWorkdayCalendar();

    @Test
    void filtersAllowDenyCrossMidnightAndHolidaySpill() {
        TimeWindow overnight = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(22 * 60, 2 * 60)),
                List.of());
        assertTrue(CalendarWindowedPreview.allows(overnight, at(2026, 1, 9, 23, 0), calendar));
        assertTrue(CalendarWindowedPreview.allows(overnight, at(2026, 1, 10, 1, 0), calendar));
        assertFalse(CalendarWindowedPreview.allows(overnight, at(2026, 1, 10, 23, 0), calendar));
        assertFalse(CalendarWindowedPreview.allows(overnight, at(2026, 1, 11, 1, 0), calendar));

        TimeWindow holidayNight = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(22 * 60, 7 * 60)),
                List.of());
        assertTrue(CalendarWindowedPreview.allows(holidayNight, at(2026, 9, 30, 23, 0), calendar));
        assertTrue(CalendarWindowedPreview.allows(holidayNight, at(2026, 10, 1, 6, 0), calendar));
        assertFalse(CalendarWindowedPreview.allows(holidayNight, at(2026, 10, 1, 23, 0), calendar));

        TimeWindow untilMidnight = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(0, 24 * 60)),
                List.of());
        assertTrue(CalendarWindowedPreview.allows(untilMidnight, at(2026, 1, 5, 23, 59), calendar));
        assertFalse(CalendarWindowedPreview.allows(untilMidnight, at(2026, 1, 10, 12, 0), calendar));

        TimeWindow denyMorning = new TimeWindow(
                TimeWindow.Mode.DENY,
                List.of(new TimeWindow.Segment(9 * 60, 10 * 60)),
                List.of());
        assertFalse(CalendarWindowedPreview.allows(denyMorning, at(2026, 1, 5, 9, 30), calendar));
        assertTrue(CalendarWindowedPreview.allows(denyMorning, at(2026, 1, 10, 9, 30), calendar));
    }

    @Test
    void nullWindowMatchesUnfilteredPreview() {
        Map<String, Object> fields = daily("09:30:00");
        Rule version2 = CalendarConfigParser.parse(fields, 1);
        fields.put("timeWindow", null);
        Rule version3 = CalendarConfigParser.parseBinding(fields, 3, "reminder").rule();
        Instant after = Instant.parse("2026-01-05T00:00:00Z");
        List<String> left = CalendarOccurrenceCalculator.preview(version2, after, 7).stream()
                .map(Occurrence::occurrenceKey)
                .toList();
        List<String> right = CalendarWindowedPreview.preview(version3, null, after, 7).stream()
                .map(Occurrence::occurrenceKey)
                .toList();
        assertEquals(left, right);
    }

    @Test
    void minuteJumpMatchesBruteFilterAndStaysUnder50ms() {
        Rule rule = CalendarConfigParser.parse(minutes(1, "00:00:00", "2026-01-05"), 1);
        TimeWindow window = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(9 * 60, 9 * 60 + 5)),
                List.of(new TimeWindow.Segment(9 * 60, 9 * 60 + 5)));
        Instant after = Instant.parse("2026-01-05T00:00:00Z");
        Instant twoDays = after.plus(Duration.ofDays(2));
        List<String> brute = new ArrayList<>();
        Instant cursor = after;
        while (brute.size() < 20) {
            List<Occurrence> batch = CalendarOccurrenceCalculator.preview(rule, cursor, 100);
            if (batch.isEmpty()) {
                break;
            }
            for (Occurrence occ : batch) {
                if (occ.occurrenceAt().isAfter(twoDays)) {
                    break;
                }
                if (CalendarWindowedPreview.allows(window, occ.occurrenceAt(), calendar)) {
                    brute.add(occ.occurrenceKey());
                }
            }
            cursor = batch.get(batch.size() - 1).occurrenceAt();
            if (!cursor.isBefore(twoDays)) {
                break;
            }
        }
        List<String> jumped = CalendarWindowedPreview.preview(rule, window, after, 20).stream()
                .filter(o -> !o.occurrenceAt().isAfter(twoDays))
                .map(Occurrence::occurrenceKey)
                .toList();
        assertEquals(brute, jumped);
        assertFalse(jumped.isEmpty());

        TimeWindow narrow = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(3 * 60, 3 * 60 + 1)),
                List.of());
        CalendarWindowedPreview.preview(rule, narrow, after, 1);
        long started = System.nanoTime();
        List<Occurrence> found = CalendarWindowedPreview.preview(rule, narrow, after, 1);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(found.isEmpty() || !found.get(0).occurrenceAt().isAfter(after.plus(Duration.ofDays(400))));
        assertTrue(elapsedMs < 50, "elapsedMs=" + elapsedMs);
    }

    @Test
    void planWriteDefersWhenFilteredOutAndKeepsLaterHit() {
        Instant now = Instant.parse("2026-01-05T00:00:00Z");
        Instant windowEnd = now.plus(Duration.ofDays(7));
        Rule daily = CalendarConfigParser.parse(daily("09:30:00"), 1);
        TimeWindow impossible = new TimeWindow(
                TimeWindow.Mode.ALLOW, List.of(new TimeWindow.Segment(3 * 60, 3 * 60 + 1)), List.of());
        CalendarWindowedPreview.WritePlan deferred =
                CalendarWindowedPreview.planWrite(daily, impossible, now, windowEnd, false);
        assertFalse(deferred.exhausted());
        assertEquals(now.plus(Duration.ofDays(400)), deferred.nextFireAt());
        assertTrue(deferred.inWindow().isEmpty());
        CalendarConfigException missing = assertThrows(
                CalendarConfigException.class,
                () -> CalendarWindowedPreview.requireWithinHorizon(daily, impossible, now));
        assertTrue(missing.getMessage().startsWith("TW_NO_OCCURRENCE："));

        Rule sparse = CalendarConfigParser.parse(everyNDays(30, "2026-02-10", "09:00:00"), 1);
        TimeWindow allDay = new TimeWindow(
                TimeWindow.Mode.ALLOW,
                List.of(new TimeWindow.Segment(0, 24 * 60)),
                List.of(new TimeWindow.Segment(0, 24 * 60)));
        CalendarWindowedPreview.WritePlan later =
                CalendarWindowedPreview.planWrite(sparse, allDay, now, windowEnd, false);
        assertFalse(later.exhausted());
        assertTrue(later.inWindow().isEmpty());
        assertTrue(later.nextFireAt().isAfter(windowEnd));
    }

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    private static Map<String, Object> daily(String localTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "DAILY");
        m.put("startDate", "2026-01-01");
        m.put("localTime", localTime);
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    private static Map<String, Object> minutes(int interval, String localTime, String startDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "EVERY_N_MINUTES");
        m.put("startDate", startDate);
        m.put("intervalMinutes", interval);
        m.put("localTime", localTime);
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    private static Map<String, Object> everyNDays(int interval, String startDate, String localTime) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "EVERY_N_DAYS");
        m.put("startDate", startDate);
        m.put("intervalDays", interval);
        m.put("localTime", localTime);
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }
}
