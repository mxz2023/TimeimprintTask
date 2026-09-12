package cn.net.mxz.timeimprint.task.service.capability.calendar;

import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Daily;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.EveryNDays;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Monthly;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Once;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Rule;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator.Weekly;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

/** 将 calendar schemaVersion=1 配置解码为计算器 Rule。 */
public final class MxzCalendarConfigParser {

    private MxzCalendarConfigParser() {}

    public static Rule parse(ScenarioMutationPayload payload) {
        if (!(payload instanceof MxzJsonPayload json)) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar config must be object");
        }
        return parse(json.fields());
    }

    public static Rule parse(Map<String, Object> fields) {
        Object typeObj = fields.get("type");
        if (typeObj == null) {
            throw new IllegalArgumentException("INVALID_REQUEST: calendar type required");
        }
        String type = String.valueOf(typeObj).toUpperCase(Locale.ROOT);
        ZoneId zone = ZoneId.of(requireString(fields, "zoneId"));
        LocalTime localTime = LocalTime.parse(requireString(fields, "localTime"));
        return switch (type) {
            case "ONCE" -> new Once(LocalDate.parse(requireString(fields, "localDate")), localTime, zone);
            case "DAILY" -> new Daily(LocalDate.parse(requireString(fields, "startDate")), localTime, zone);
            case "WEEKLY" -> new Weekly(
                    LocalDate.parse(requireString(fields, "startDate")),
                    requireInt(fields, "weekday"),
                    localTime,
                    zone);
            case "MONTHLY" -> new Monthly(
                    LocalDate.parse(requireString(fields, "startDate")),
                    requireInt(fields, "dayOfMonth"),
                    localTime,
                    zone);
            case "EVERY_N_DAYS" -> new EveryNDays(
                    LocalDate.parse(requireString(fields, "startDate")),
                    requireInt(fields, "intervalDays"),
                    localTime,
                    zone);
            default -> throw new IllegalArgumentException("INVALID_REQUEST: unknown calendar type " + type);
        };
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
        return Integer.parseInt(String.valueOf(v));
    }
}
