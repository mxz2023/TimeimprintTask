package cn.net.mxz.timeimprint.task.service.capability.calendar;

import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Daily;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.EveryNDays;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Monthly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Once;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Rule;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator.Weekly;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将 calendar schemaVersion=1 配置解码为计算器 Rule。 */
public final class CalendarConfigParser {

    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Set<String> ONCE_KEYS = Set.of("type", "localDate", "localTime", "zoneId");
    private static final Set<String> DAILY_KEYS = Set.of("type", "startDate", "localTime", "zoneId");
    private static final Set<String> WEEKLY_KEYS = Set.of("type", "startDate", "weekday", "localTime", "zoneId");
    private static final Set<String> MONTHLY_KEYS = Set.of("type", "startDate", "dayOfMonth", "localTime", "zoneId");
    private static final Set<String> EVERY_N_KEYS = Set.of("type", "startDate", "intervalDays", "localTime", "zoneId");

    private CalendarConfigParser() {}

    public static Rule parse(ScenarioMutationPayload payload) {
        if (!(payload instanceof JsonPayload json)) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config must be object");
        }
        return parse(json.fields());
    }

    public static Rule parse(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config required");
        }
        Object typeObj = fields.get("type");
        if (typeObj == null) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar type required");
        }
        String type = String.valueOf(typeObj).toUpperCase(Locale.ROOT);
        Set<String> allowed = switch (type) {
            case "ONCE" -> ONCE_KEYS;
            case "DAILY" -> DAILY_KEYS;
            case "WEEKLY" -> WEEKLY_KEYS;
            case "MONTHLY" -> MONTHLY_KEYS;
            case "EVERY_N_DAYS" -> EVERY_N_KEYS;
            default -> throw new IllegalArgumentException("INVALID_REQUEST: unknown calendar type " + type);
        };
        rejectUnknownKeys(fields, allowed);

        ZoneId zone = ZoneId.of(requireString(fields, "zoneId"));
        if (!CalendarOccurrenceCalculator.BUSINESS_ZONE.equals(zone)) {
            throw new IllegalArgumentException("INVALID_REQUEST: zoneId must be Asia/Shanghai in P01");
        }
        LocalTime localTime = parseLocalTime(requireString(fields, "localTime"));
        return switch (type) {
            case "ONCE" -> new Once(parseLocalDate(requireString(fields, "localDate")), localTime, zone);
            case "DAILY" -> new Daily(parseLocalDate(requireString(fields, "startDate")), localTime, zone);
            case "WEEKLY" -> {
                int weekday = requireInt(fields, "weekday");
                if (weekday < 1 || weekday > 7) {
                    throw new IllegalArgumentException("INVALID_REQUEST: weekday must be 1..7");
                }
                yield new Weekly(parseLocalDate(requireString(fields, "startDate")), weekday, localTime, zone);
            }
            case "MONTHLY" -> {
                int dayOfMonth = requireInt(fields, "dayOfMonth");
                if (dayOfMonth < 1 || dayOfMonth > 31) {
                    throw new IllegalArgumentException("INVALID_REQUEST: dayOfMonth must be 1..31");
                }
                yield new Monthly(parseLocalDate(requireString(fields, "startDate")), dayOfMonth, localTime, zone);
            }
            case "EVERY_N_DAYS" -> {
                int intervalDays = requireInt(fields, "intervalDays");
                if (intervalDays < 1 || intervalDays > 3650) {
                    throw new IllegalArgumentException("INVALID_REQUEST: intervalDays must be 1..3650");
                }
                yield new EveryNDays(
                        parseLocalDate(requireString(fields, "startDate")), intervalDays, localTime, zone);
            }
            default -> throw new IllegalArgumentException("INVALID_REQUEST: unknown calendar type " + type);
        };
    }

    private static void rejectUnknownKeys(Map<String, Object> fields, Set<String> allowed) {
        for (String key : fields.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("INVALID_REQUEST: unknown calendar field " + key);
            }
        }
    }

    private static LocalDate parseLocalDate(String raw) {
        try {
            return LocalDate.parse(raw, LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("INVALID_REQUEST: date must be yyyy-MM-dd");
        }
    }

    private static LocalTime parseLocalTime(String raw) {
        try {
            return LocalTime.parse(raw, LOCAL_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("INVALID_REQUEST: localTime must be HH:mm:ss");
        }
    }

    private static String requireString(Map<String, Object> fields, String key) {
        Object v = fields.get(key);
        if (v == null) {
            throw new IllegalArgumentException("INVALID_REQUEST: missing " + key);
        }
        return String.valueOf(v);
    }

    private static int requireInt(Map<String, Object> fields, String key) {
        Object v = fields.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            throw new IllegalArgumentException("INVALID_REQUEST: missing " + key);
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("INVALID_REQUEST: " + key + " must be integer");
        }
    }
}
