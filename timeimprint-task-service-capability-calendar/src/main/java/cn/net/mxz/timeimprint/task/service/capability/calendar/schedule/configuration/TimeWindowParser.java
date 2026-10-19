package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 解码 timeWindow。null 表示不限时段。 */
final class TimeWindowParser {

    private static final Set<String> WINDOW_KEYS = Set.of("mode", "workdaySegments", "nonWorkdaySegments");
    private static final Set<String> SEGMENT_KEYS = Set.of("start", "end");
    private static final Pattern CLOCK = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private static final int MAX_SEGMENTS = 8;

    private TimeWindowParser() {}

    static TimeWindow parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw CalendarConfigException.invalid("TW_FIELD", "timeWindow 必须是对象或 null");
        }
        for (Object key : map.keySet()) {
            if (!WINDOW_KEYS.contains(String.valueOf(key))) {
                throw CalendarConfigException.invalid("TW_FIELD", "timeWindow 含有未知字段 " + key);
            }
        }
        if (!map.containsKey("mode") || !map.containsKey("workdaySegments") || !map.containsKey("nonWorkdaySegments")) {
            throw CalendarConfigException.invalid("TW_FIELD", "timeWindow 必须包含 mode、workdaySegments、nonWorkdaySegments");
        }
        TimeWindow.Mode mode = readMode(map.get("mode"));
        List<TimeWindow.Segment> workday = readSegments(map.get("workdaySegments"), "工作日");
        List<TimeWindow.Segment> nonWorkday = readSegments(map.get("nonWorkdaySegments"), "非工作日");
        if (workday.isEmpty() && nonWorkday.isEmpty()) {
            if (mode == TimeWindow.Mode.ALLOW) {
                throw CalendarConfigException.invalid("TW_ALLOW_EMPTY", "ALLOW 模式下两套时间段不能都为空");
            }
            throw CalendarConfigException.invalid("TW_DENY_EMPTY", "DENY 模式下两套时间段不能都为空，不限时段请传 null");
        }
        return new TimeWindow(mode, workday, nonWorkday);
    }

    private static TimeWindow.Mode readMode(Object raw) {
        if (!(raw instanceof String text) || (!"ALLOW".equals(text) && !"DENY".equals(text))) {
            throw CalendarConfigException.invalid("TW_MODE", "mode 只能是 ALLOW 或 DENY");
        }
        return TimeWindow.Mode.valueOf(text);
    }

    private static List<TimeWindow.Segment> readSegments(Object raw, String label) {
        if (!(raw instanceof List<?> list)) {
            throw CalendarConfigException.invalid("TW_FIELD", label + "时间段必须是数组");
        }
        if (list.size() > MAX_SEGMENTS) {
            throw CalendarConfigException.invalid("TW_TOO_MANY", label + "时间段最多 8 段");
        }
        List<TimeWindow.Segment> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(readSegment(item, label));
        }
        for (int i = 1; i < out.size(); i++) {
            if (out.get(i).startMinute() <= out.get(i - 1).startMinute()) {
                throw CalendarConfigException.invalid("TW_ORDER", label + "时间段必须按开始时间严格升序");
            }
        }
        for (int i = 0; i < out.size(); i++) {
            for (int j = i + 1; j < out.size(); j++) {
                if (overlaps(out.get(i), out.get(j))) {
                    TimeWindow.Segment left = out.get(i);
                    TimeWindow.Segment right = out.get(j);
                    throw CalendarConfigException.invalid(
                            "TW_OVERLAP",
                            label
                                    + "时间段 "
                                    + left.formatStart()
                                    + "—"
                                    + left.formatEnd()
                                    + " 与 "
                                    + right.formatStart()
                                    + "—"
                                    + right.formatEnd()
                                    + " 重叠");
                }
            }
        }
        return out;
    }

    private static TimeWindow.Segment readSegment(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw CalendarConfigException.invalid("TW_FIELD", label + "时间段必须是对象");
        }
        for (Object key : map.keySet()) {
            if (!SEGMENT_KEYS.contains(String.valueOf(key))) {
                throw CalendarConfigException.invalid("TW_FIELD", label + "时间段含有未知字段 " + key);
            }
        }
        if (!map.containsKey("start") || !map.containsKey("end")) {
            throw CalendarConfigException.invalid("TW_FIELD", label + "时间段必须包含 start 与 end");
        }
        int start = parseStart(map.get("start"));
        int end = parseEnd(map.get("end"));
        if (start == end) {
            throw CalendarConfigException.invalid("TW_ZERO_LENGTH", label + "时间段的开始与结束不能相同");
        }
        return new TimeWindow.Segment(start, end);
    }

    private static int parseStart(Object raw) {
        String text = clockText(raw);
        if ("24:00".equals(text) || !CLOCK.matcher(text).matches()) {
            throw CalendarConfigException.invalid("TW_TIME_FORMAT", "start 须为 00:00—23:59 的 HH:mm，不能是 24:00");
        }
        return toMinute(text);
    }

    private static int parseEnd(Object raw) {
        String text = clockText(raw);
        if ("00:00".equals(text)) {
            throw CalendarConfigException.invalid("TW_TIME_FORMAT", "end 不能是 00:00，当天结束请写 24:00");
        }
        if ("24:00".equals(text)) {
            return 24 * 60;
        }
        if (!CLOCK.matcher(text).matches()) {
            throw CalendarConfigException.invalid("TW_TIME_FORMAT", "end 须为 HH:mm，范围 00:01—23:59 或 24:00");
        }
        return toMinute(text);
    }

    private static String clockText(Object raw) {
        if (!(raw instanceof String text)) {
            throw CalendarConfigException.invalid("TW_TIME_FORMAT", "时间必须是 HH:mm 字符串");
        }
        return text;
    }

    private static int toMinute(String hhmm) {
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        int minute = Integer.parseInt(hhmm.substring(3, 5));
        return hour * 60 + minute;
    }

    /** 24 小时环上的半开区间是否重叠。端点相接不算重叠。 */
    static boolean overlaps(TimeWindow.Segment left, TimeWindow.Segment right) {
        for (int[] a : expand(left)) {
            for (int[] b : expand(right)) {
                if (a[0] < b[1] && b[0] < a[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<int[]> expand(TimeWindow.Segment segment) {
        if (segment.endMinute() > segment.startMinute()) {
            return List.of(new int[] {segment.startMinute(), segment.endMinute()});
        }
        List<int[]> parts = new ArrayList<>(2);
        parts.add(new int[] {segment.startMinute(), 24 * 60});
        if (segment.endMinute() > 0) {
            parts.add(new int[] {0, segment.endMinute()});
        }
        return parts;
    }
}
