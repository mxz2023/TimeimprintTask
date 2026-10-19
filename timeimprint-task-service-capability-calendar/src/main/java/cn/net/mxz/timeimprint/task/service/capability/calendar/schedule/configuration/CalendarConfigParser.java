package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Daily;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNHours;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNMinutes;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNDays;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.AnchorOffsets;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Monthly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Once;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Rule;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Weekly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Yearly;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 将 calendar 配置解码为计算器 Rule。schemaVersion 3 另带 timeWindow。 */
public final class CalendarConfigParser {

    /** schemaVersion 3 且 timeWindow 为 null 时，timeWindow 字段为 null，发生与版本 2 相同。 */
    public record ParsedBinding(Rule rule, TimeWindow timeWindow) {}

    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Set<String> ONCE_KEYS = Set.of("type", "localDate", "localTime", "zoneId");
    private static final Set<String> DAILY_KEYS = Set.of("type", "startDate", "localTime", "zoneId");
    private static final Set<String> WEEKLY_KEYS = Set.of("type", "startDate", "weekday", "localTime", "zoneId");
    private static final Set<String> MONTHLY_KEYS = Set.of("type", "startDate", "dayOfMonth", "localTime", "zoneId");
    private static final Set<String> EVERY_N_KEYS = Set.of("type", "startDate", "intervalDays", "localTime", "zoneId");
    private static final Set<String> EVERY_N_HOURS_KEYS =
            Set.of("type", "startDate", "intervalHours", "localTime", "zoneId");
    private static final Set<String> EVERY_N_MINUTES_KEYS =
            Set.of("type", "startDate", "intervalMinutes", "localTime", "zoneId");
    private static final Set<String> YEARLY_KEYS =
            Set.of("type", "startDate", "month", "dayOfMonth", "offsetDays", "localTime", "zoneId");
    private static final Set<String> ANCHOR_KEYS =
            Set.of("type", "anchorDate", "offsetDays", "dailyAfterAnchor", "localTime", "zoneId");

    private CalendarConfigParser() {}

    public static Rule parse(ScenarioMutationPayload payload) {
        return parse(payload, 1);
    }

    public static Rule parse(ScenarioMutationPayload payload, int schemaVersion) {
        if (!(payload instanceof JsonPayload json)) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config must be object");
        }
        return parse(json.fields(), schemaVersion);
    }

    public static Rule parse(Map<String, Object> fields) {
        return parse(fields, 1);
    }

    public static ParsedBinding parseBinding(ScenarioMutationPayload payload, int schemaVersion, String scenarioKey) {
        if (!(payload instanceof JsonPayload json)) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config must be object");
        }
        return parseBinding(json.fields(), schemaVersion, scenarioKey);
    }

    public static ParsedBinding parseBinding(Map<String, Object> fields, int schemaVersion, String scenarioKey) {
        if (schemaVersion == 3 && scenarioKey != null && !acceptsSchema3(scenarioKey)) {
            throw CalendarConfigException.unsupportedSchema();
        }
        if (schemaVersion == 3) {
            if (fields == null || !fields.containsKey("timeWindow")) {
                throw CalendarConfigException.invalid("TW_FIELD", "schemaVersion 3 必须包含 timeWindow");
            }
            Map<String, Object> copy = new LinkedHashMap<>(fields);
            TimeWindow window = TimeWindowParser.parse(copy.remove("timeWindow"));
            return new ParsedBinding(parse(copy, 2), window);
        }
        return new ParsedBinding(parse(fields, schemaVersion), null);
    }

    public static boolean acceptsSchema3(String scenarioKey) {
        return "reminder".equals(scenarioKey) || "recurring_todo".equals(scenarioKey);
    }

    public static Rule parse(Map<String, Object> fields, int schemaVersion) {
        if (schemaVersion == 3) {
            return parseBinding(fields, 3, null).rule();
        }
        if (schemaVersion != 1 && schemaVersion != 2) {
            throw new IllegalArgumentException("INVALID_REQUEST: unsupported calendar schemaVersion");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config required");
        }
        Object typeObj = fields.get("type");
        if (typeObj == null) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar type required");
        }
        String type = String.valueOf(typeObj).toUpperCase(Locale.ROOT);
        if (schemaVersion == 1 && ("YEARLY".equals(type) || "ANCHOR_OFFSETS".equals(type))) {
            throw new IllegalArgumentException("INVALID_REQUEST: unknown calendar type " + type);
        }
        Set<String> allowed = switch (type) {
            case "ONCE" -> ONCE_KEYS;
            case "DAILY" -> DAILY_KEYS;
            case "WEEKLY" -> WEEKLY_KEYS;
            case "MONTHLY" -> MONTHLY_KEYS;
            case "EVERY_N_DAYS" -> EVERY_N_KEYS;
            case "EVERY_N_HOURS" -> EVERY_N_HOURS_KEYS;
            case "EVERY_N_MINUTES" -> EVERY_N_MINUTES_KEYS;
            case "YEARLY" -> YEARLY_KEYS;
            case "ANCHOR_OFFSETS" -> ANCHOR_KEYS;
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
            case "EVERY_N_HOURS" -> {
                int intervalHours = requireInt(fields, "intervalHours");
                if (intervalHours < 1 || intervalHours > 168) {
                    throw new IllegalArgumentException("INVALID_REQUEST: intervalHours must be 1..168");
                }
                yield new EveryNHours(
                        parseLocalDate(requireString(fields, "startDate")), intervalHours, localTime, zone);
            }
            case "EVERY_N_MINUTES" -> {
                int intervalMinutes = requireInt(fields, "intervalMinutes");
                if (intervalMinutes < 1 || intervalMinutes > 1440) {
                    throw new IllegalArgumentException("INVALID_REQUEST: intervalMinutes must be 1..1440");
                }
                yield new EveryNMinutes(
                        parseLocalDate(requireString(fields, "startDate")), intervalMinutes, localTime, zone);
            }
            case "YEARLY" -> {
                int month = requireInt(fields, "month");
                int day = requireInt(fields, "dayOfMonth");
                if (month < 1 || month > 12) {
                    throw new IllegalArgumentException("INVALID_REQUEST: month must be 1..12");
                }
                if (!validAnniversaryDay(month, day)) {
                    throw new IllegalArgumentException("INVALID_REQUEST: dayOfMonth is not valid for month");
                }
                yield new Yearly(
                        parseLocalDate(requireString(fields, "startDate")),
                        month,
                        day,
                        readOffsets(fields),
                        localTime,
                        zone);
            }
            case "ANCHOR_OFFSETS" -> new AnchorOffsets(
                    parseLocalDate(requireString(fields, "anchorDate")),
                    readOffsets(fields),
                    requireBoolean(fields, "dailyAfterAnchor"),
                    localTime,
                    zone);
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

    private static boolean validAnniversaryDay(int month, int day) {
        if (month == 2 && day == 29) {
            return true;
        }
        return day >= 1 && day <= LocalDate.of(2021, month, 1).lengthOfMonth();
    }

    private static List<Integer> readOffsets(Map<String, Object> fields) {
        Object raw = fields.get("offsetDays");
        if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > 4) {
            throw new IllegalArgumentException("INVALID_REQUEST: offsetDays must contain 1..4 integers");
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Object item : list) {
            int value = item instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(item));
            if (value < -366 || value > 366) {
                throw new IllegalArgumentException("INVALID_REQUEST: offsetDays item out of range");
            }
            if (!sorted.add(value)) {
                throw new IllegalArgumentException("INVALID_REQUEST: offsetDays must be unique");
            }
        }
        return new ArrayList<>(sorted);
    }

    private static boolean requireBoolean(Map<String, Object> fields, String key) {
        Object v = fields.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException("INVALID_REQUEST: " + key + " must be boolean");
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
