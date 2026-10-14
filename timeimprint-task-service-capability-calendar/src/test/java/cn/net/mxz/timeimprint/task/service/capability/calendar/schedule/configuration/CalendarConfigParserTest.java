package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNHours;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNMinutes;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Weekly;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A37：calendar schemaVersion=1 字段白名单与非法锚点。 */
class CalendarConfigParserTest {

    @Test
    void parsesSubDayIntervalsAndRejectsIllegalBounds() {
        Map<String, Object> hours = baseEveryNHours();
        EveryNHours hourRule = (EveryNHours) CalendarConfigParser.parse(hours);
        assertEquals(2, hourRule.intervalHours());

        Map<String, Object> minutes = baseEveryNMinutes();
        EveryNMinutes minuteRule = (EveryNMinutes) CalendarConfigParser.parse(minutes);
        assertEquals(15, minuteRule.intervalMinutes());

        Map<String, Object> badHours = baseEveryNHours();
        badHours.put("intervalHours", 0);
        assertInvalid(badHours, "intervalHours must be 1..168");

        Map<String, Object> badHoursHigh = baseEveryNHours();
        badHoursHigh.put("intervalHours", 169);
        assertInvalid(badHoursHigh, "intervalHours must be 1..168");

        Map<String, Object> badMinutes = baseEveryNMinutes();
        badMinutes.put("intervalMinutes", 1441);
        assertInvalid(badMinutes, "intervalMinutes must be 1..1440");

        Map<String, Object> extraField = baseEveryNHours();
        extraField.put("intervalDays", 1);
        assertInvalid(extraField, "unknown calendar field");
    }

    @Test
    void parsesWeeklyAndRejectsUnknownMissingIllegal() {
        Map<String, Object> ok = baseWeekly();
        Weekly rule = (Weekly) CalendarConfigParser.parse(ok);
        assertEquals(5, rule.weekday());

        Map<String, Object> missing = baseWeekly();
        missing.remove("weekday");
        assertInvalid(missing, "missing weekday");

        Map<String, Object> unknown = baseWeekly();
        unknown.put("daysOfWeek", "FRIDAY");
        assertInvalid(unknown, "unknown calendar field");

        Map<String, Object> badWeekday = baseWeekly();
        badWeekday.put("weekday", 0);
        assertInvalid(badWeekday, "weekday must be 1..7");

        Map<String, Object> badZone = baseWeekly();
        badZone.put("zoneId", "UTC");
        assertInvalid(badZone, "Asia/Shanghai");

        Map<String, Object> badTime = baseWeekly();
        badTime.put("localTime", "09:00");
        assertInvalid(badTime, "HH:mm:ss");

        Map<String, Object> onceWrongKey = new LinkedHashMap<>();
        onceWrongKey.put("type", "ONCE");
        onceWrongKey.put("startDate", "2026-09-15");
        onceWrongKey.put("localTime", "09:00:00");
        onceWrongKey.put("zoneId", "Asia/Shanghai");
        assertInvalid(onceWrongKey, "unknown calendar field");
    }

    private static Map<String, Object> baseEveryNHours() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "EVERY_N_HOURS");
        m.put("startDate", "2026-09-01");
        m.put("intervalHours", 2);
        m.put("localTime", "09:00:00");
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    private static Map<String, Object> baseEveryNMinutes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "EVERY_N_MINUTES");
        m.put("startDate", "2026-09-01");
        m.put("intervalMinutes", 15);
        m.put("localTime", "09:00:00");
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    private static Map<String, Object> baseWeekly() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "WEEKLY");
        m.put("startDate", "2026-09-08");
        m.put("weekday", 5);
        m.put("localTime", "09:00:00");
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    @Test
    void schema1RejectsYearlyAndSchema2AcceptsIt() {
        Map<String, Object> yearly = new LinkedHashMap<>();
        yearly.put("type", "YEARLY");
        yearly.put("startDate", "2024-02-29");
        yearly.put("month", 2);
        yearly.put("dayOfMonth", 29);
        yearly.put("offsetDays", List.of(-1, 0));
        yearly.put("localTime", "09:00:00");
        yearly.put("zoneId", "Asia/Shanghai");
        assertInvalid(yearly, "unknown calendar type");
        assertTrue(CalendarConfigParser.parse(yearly, 2) instanceof
                cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Yearly);
    }

    private static void assertInvalid(Map<String, Object> fields, String messageContains) {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> CalendarConfigParser.parse(fields));
        assertTrue(ex.getMessage().contains(messageContains), ex.getMessage());
    }
}
