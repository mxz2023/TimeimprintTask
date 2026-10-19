package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import java.util.List;

/** schemaVersion 3 的时间段。null 表示不限时段，不使用本对象。 */
public record TimeWindow(Mode mode, List<Segment> workdaySegments, List<Segment> nonWorkdaySegments) {

    public enum Mode {
        ALLOW,
        DENY
    }

    /** 分钟从 0 起算。{@code endMinute} 可为 1440，表示 24:00。 */
    public record Segment(int startMinute, int endMinute) {
        public String formatStart() {
            return formatMinute(startMinute);
        }

        public String formatEnd() {
            return formatMinute(endMinute);
        }

        public static String formatMinute(int minute) {
            return String.format("%02d:%02d", minute / 60, minute % 60);
        }
    }

    public TimeWindow {
        workdaySegments = List.copyOf(workdaySegments);
        nonWorkdaySegments = List.copyOf(nonWorkdaySegments);
    }
}
