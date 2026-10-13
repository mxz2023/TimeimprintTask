package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation;

import java.util.Locale;
import java.util.Map;

/** WAITING 实例初始 scenario 快照：小时/分钟间隔规则禁用稍后。 */
public final class CalendarPlannedInstanceSnapshot {

    private CalendarPlannedInstanceSnapshot() {}

    public static boolean snoozeDisallowed(Map<String, Object> calendarConfig) {
        if (calendarConfig == null) {
            return false;
        }
        Object typeObj = calendarConfig.get("type");
        if (typeObj == null) {
            return false;
        }
        return snoozeDisallowedType(String.valueOf(typeObj));
    }

    public static boolean snoozeDisallowedType(String type) {
        String normalized = type.toUpperCase(Locale.ROOT);
        return "EVERY_N_HOURS".equals(normalized) || "EVERY_N_MINUTES".equals(normalized);
    }

    public static String json(String occurrenceKey, Map<String, Object> calendarConfig) {
        if (snoozeDisallowed(calendarConfig)) {
            return "{\"occurrenceKey\":\"" + occurrenceKey + "\",\"snoozeAllowed\":false}";
        }
        return "{\"occurrenceKey\":\"" + occurrenceKey + "\"}";
    }
}
