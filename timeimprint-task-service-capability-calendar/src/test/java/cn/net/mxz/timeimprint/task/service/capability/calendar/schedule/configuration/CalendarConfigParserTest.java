package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void schema3AcceptsNineTypesAndNullWindow() {
        Map<String, String> names = Map.of(
                "ONCE", "Once",
                "DAILY", "Daily",
                "WEEKLY", "Weekly",
                "MONTHLY", "Monthly",
                "EVERY_N_DAYS", "EveryNDays",
                "EVERY_N_HOURS", "EveryNHours",
                "EVERY_N_MINUTES", "EveryNMinutes",
                "YEARLY", "Yearly",
                "ANCHOR_OFFSETS", "AnchorOffsets");
        for (var entry : names.entrySet()) {
            Map<String, Object> fields = schema3(entry.getKey(), null);
            CalendarConfigParser.ParsedBinding parsed =
                    CalendarConfigParser.parseBinding(fields, 3, "reminder");
            assertEquals(entry.getValue(), parsed.rule().getClass().getSimpleName());
            assertNull(parsed.timeWindow());
            assertTrue(CalendarConfigParser.parse(fields, 3) != null);
        }
        CalendarConfigParser.ParsedBinding allow =
                CalendarConfigParser.parseBinding(schema3("DAILY", window("ALLOW", List.of(seg("09:00", "10:00")), List.of())), 3, "recurring_todo");
        assertEquals(TimeWindow.Mode.ALLOW, allow.timeWindow().mode());
    }

    @Test
    void schema3RejectsUnsupportedScenarioBeforeFieldErrors() {
        Map<String, Object> missing = dailyCore();
        CalendarConfigException ex = assertThrows(
                CalendarConfigException.class,
                () -> CalendarConfigParser.parseBinding(missing, 3, "anniversary"));
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", ex.errorCode());
        for (String scenario : List.of("deadline", "payment", "maintenance")) {
            CalendarConfigException rejected = assertThrows(
                    CalendarConfigException.class,
                    () -> CalendarConfigParser.parseBinding(missing, 3, scenario));
            assertEquals("UNSUPPORTED_SCHEMA_VERSION", rejected.errorCode());
        }
    }

    @Test
    void schema3ReasonKeys() {
        assertReason("TW_FIELD", dailyCore());
        Map<String, Object> unknown = schema3("DAILY", window("ALLOW", List.of(seg("09:00", "10:00")), List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> unknownWindow = (Map<String, Object>) unknown.get("timeWindow");
        unknownWindow.put("extra", 1);
        assertReason("TW_FIELD", unknown);
        assertReason("TW_MODE", schema3("DAILY", window("allow", List.of(seg("09:00", "10:00")), List.of())));
        assertReason("TW_TIME_FORMAT", schema3("DAILY", window("ALLOW", List.of(seg("24:00", "10:00")), List.of())));
        assertReason("TW_TIME_FORMAT", schema3("DAILY", window("ALLOW", List.of(seg("09:00", "00:00")), List.of())));
        assertReason("TW_ZERO_LENGTH", schema3("DAILY", window("ALLOW", List.of(seg("09:00", "09:00")), List.of())));
        assertReason(
                "TW_TOO_MANY",
                schema3(
                        "DAILY",
                        window(
                                "ALLOW",
                                List.of(
                                        seg("00:00", "01:00"),
                                        seg("02:00", "03:00"),
                                        seg("04:00", "05:00"),
                                        seg("06:00", "07:00"),
                                        seg("08:00", "09:00"),
                                        seg("10:00", "11:00"),
                                        seg("12:00", "13:00"),
                                        seg("14:00", "15:00"),
                                        seg("16:00", "17:00")),
                                List.of())));
        assertReason(
                "TW_ORDER",
                schema3("DAILY", window("ALLOW", List.of(seg("11:00", "12:00"), seg("09:00", "10:00")), List.of())));
        assertReason(
                "TW_OVERLAP",
                schema3("DAILY", window("ALLOW", List.of(seg("09:00", "12:00"), seg("11:00", "13:00")), List.of())));
        assertReason("TW_ALLOW_EMPTY", schema3("DAILY", window("ALLOW", List.of(), List.of())));
        assertReason("TW_DENY_EMPTY", schema3("DAILY", window("DENY", List.of(), List.of())));
        Map<String, Object> touching =
                schema3("DAILY", window("ALLOW", List.of(seg("09:00", "12:00"), seg("12:00", "13:00")), List.of()));
        assertEquals(2, CalendarConfigParser.parseBinding(touching, 3, "reminder").timeWindow().workdaySegments().size());
    }

    @Test
    void schema1StillRejectsYearlyAndTimeWindowField() {
        Map<String, Object> withWindow = dailyCore();
        withWindow.put("timeWindow", null);
        assertInvalid(withWindow, "unknown calendar field");
    }

    private static void assertReason(String key, Map<String, Object> fields) {
        CalendarConfigException ex = assertThrows(
                CalendarConfigException.class, () -> CalendarConfigParser.parseBinding(fields, 3, "reminder"));
        assertEquals("INVALID_REQUEST", ex.errorCode());
        assertTrue(ex.getMessage().startsWith(key + "："), ex.getMessage());
    }

    private static Map<String, Object> schema3(String type, Object timeWindow) {
        Map<String, Object> fields = switch (type) {
            case "ONCE" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "ONCE");
                m.put("localDate", "2026-06-01");
                m.put("localTime", "09:00:00");
                m.put("zoneId", "Asia/Shanghai");
                yield m;
            }
            case "WEEKLY" -> baseWeekly();
            case "MONTHLY" -> {
                Map<String, Object> m = dailyCore();
                m.put("type", "MONTHLY");
                m.put("dayOfMonth", 15);
                yield m;
            }
            case "EVERY_N_DAYS" -> {
                Map<String, Object> m = dailyCore();
                m.put("type", "EVERY_N_DAYS");
                m.put("intervalDays", 2);
                yield m;
            }
            case "EVERY_N_HOURS" -> baseEveryNHours();
            case "EVERY_N_MINUTES" -> baseEveryNMinutes();
            case "YEARLY" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "YEARLY");
                m.put("startDate", "2026-01-01");
                m.put("month", 2);
                m.put("dayOfMonth", 29);
                m.put("offsetDays", List.of(0));
                m.put("localTime", "09:00:00");
                m.put("zoneId", "Asia/Shanghai");
                yield m;
            }
            case "ANCHOR_OFFSETS" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "ANCHOR_OFFSETS");
                m.put("anchorDate", "2026-06-01");
                m.put("offsetDays", List.of(0));
                m.put("dailyAfterAnchor", false);
                m.put("localTime", "09:00:00");
                m.put("zoneId", "Asia/Shanghai");
                yield m;
            }
            default -> dailyCore();
        };
        fields.put("timeWindow", timeWindow);
        return fields;
    }

    private static Map<String, Object> dailyCore() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "DAILY");
        m.put("startDate", "2026-01-01");
        m.put("localTime", "09:30:00");
        m.put("zoneId", "Asia/Shanghai");
        return m;
    }

    private static Map<String, Object> window(String mode, List<Map<String, String>> workday, List<Map<String, String>> nonWorkday) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode);
        m.put("workdaySegments", workday);
        m.put("nonWorkdaySegments", nonWorkday);
        return m;
    }

    private static Map<String, String> seg(String start, String end) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("start", start);
        m.put("end", end);
        return m;
    }

    private static void assertInvalid(Map<String, Object> fields, String messageContains) {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> CalendarConfigParser.parse(fields));
        assertTrue(ex.getMessage().contains(messageContains), ex.getMessage());
    }
}
