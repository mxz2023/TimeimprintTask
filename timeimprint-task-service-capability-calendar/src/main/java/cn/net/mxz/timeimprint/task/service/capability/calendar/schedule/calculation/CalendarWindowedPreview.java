package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNHours;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.EveryNMinutes;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Occurrence;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation.CalendarOccurrenceCalculator.Rule;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigException;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CnMainlandWorkdayCalendar;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.TimeWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 在原规则候选上按时间段删减。{@code timeWindow == null} 时直接调用原计算器，结果与无时段相同。
 * 小时、分钟规则跳到下一段起点再对齐锚点。一次计算最多向前 400 天。
 */
public final class CalendarWindowedPreview {

    public static final int SEARCH_DAYS = 400;
    private static final ZoneId ZONE = CalendarOccurrenceCalculator.BUSINESS_ZONE;
    private static final int DAY_SECONDS = 24 * 60 * 60;

    private CalendarWindowedPreview() {}

    public record WritePlan(List<Occurrence> inWindow, Instant nextFireAt, boolean exhausted) {}

    public static void requireWithinHorizon(Rule rule, TimeWindow window, Instant now) {
        if (window == null) {
            return;
        }
        if (preview(rule, window, now, 1).isEmpty()) {
            throw CalendarConfigException.invalid("TW_NO_OCCURRENCE", "从当前时间起 400 天内没有允许的发生");
        }
    }

    public static List<Occurrence> preview(Rule rule, TimeWindow window, Instant afterExclusive, int limit) {
        if (window == null) {
            return CalendarOccurrenceCalculator.preview(rule, afterExclusive, limit);
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be 1..100");
        }
        CnMainlandWorkdayCalendar calendar = CnMainlandWorkdayCalendarHolder.get();
        Instant horizon = afterExclusive.plus(Duration.ofDays(SEARCH_DAYS));
        if (rule instanceof EveryNHours hours) {
            return previewInterval(
                    hours.startDate(),
                    hours.localTime(),
                    hours.zoneId(),
                    Duration.ofHours(hours.intervalHours()),
                    window,
                    calendar,
                    afterExclusive,
                    horizon,
                    limit);
        }
        if (rule instanceof EveryNMinutes minutes) {
            return previewInterval(
                    minutes.startDate(),
                    minutes.localTime(),
                    minutes.zoneId(),
                    Duration.ofMinutes(minutes.intervalMinutes()),
                    window,
                    calendar,
                    afterExclusive,
                    horizon,
                    limit);
        }
        return previewByFiltering(rule, window, calendar, afterExclusive, horizon, limit);
    }

    /**
     * 创建、修改、恢复使用的 7 天窗口与游标。仅用于非 null 的 timeWindow。
     * 原规则没有后续发生才标 exhausted；400 天内被滤空时不标耗尽，nextFireAt 为搜索终点。
     */
    public static WritePlan planWrite(
            Rule rule, TimeWindow window, Instant now, Instant windowEnd, boolean once) {
        Instant horizon = now.plus(Duration.ofDays(SEARCH_DAYS));
        List<Occurrence> allowed = preview(rule, window, now, 100);
        List<Occurrence> inWindow = new ArrayList<>();
        Occurrence firstAfterWindow = null;
        for (Occurrence occ : allowed) {
            if (!occ.occurrenceAt().isAfter(windowEnd)) {
                inWindow.add(occ);
            } else if (firstAfterWindow == null) {
                firstAfterWindow = occ;
                break;
            }
        }
        boolean capped = allowed.size() == 100 && firstAfterWindow == null;
        if (once) {
            if (!inWindow.isEmpty()) {
                return new WritePlan(inWindow, inWindow.get(0).occurrenceAt(), true);
            }
            if (firstAfterWindow != null) {
                return new WritePlan(List.of(), firstAfterWindow.occurrenceAt(), false);
            }
            return deferredOrDone(rule, now, horizon, List.of());
        }
        if (CalendarOccurrenceCalculator.openEnded(rule)) {
            if (firstAfterWindow != null) {
                return new WritePlan(inWindow, firstAfterWindow.occurrenceAt(), false);
            }
            if (capped && !inWindow.isEmpty()) {
                return new WritePlan(
                        inWindow, inWindow.get(inWindow.size() - 1).occurrenceAt().plusSeconds(1), false);
            }
            Instant cursor = inWindow.isEmpty() ? windowEnd : inWindow.get(inWindow.size() - 1).occurrenceAt();
            List<Occurrence> more = preview(rule, window, cursor, 1);
            if (!more.isEmpty() && !more.get(0).occurrenceAt().isAfter(horizon)) {
                return new WritePlan(inWindow, more.get(0).occurrenceAt(), false);
            }
            return deferredOrDone(rule, cursor, horizon, inWindow);
        }
        if (!inWindow.isEmpty()) {
            return new WritePlan(inWindow, inWindow.get(0).occurrenceAt(), false);
        }
        if (firstAfterWindow != null) {
            return new WritePlan(List.of(), firstAfterWindow.occurrenceAt(), false);
        }
        return deferredOrDone(rule, now, horizon, List.of());
    }

    /**
     * Planner 在已有窗口之外推进游标。{@code materializedLast} 为本次写入的最后一次；没有写入时为 null。
     */
    public static WritePlan planCatchUp(
            Rule rule,
            TimeWindow window,
            Instant now,
            Instant cursorExclusive,
            Instant materializedLast,
            int generated,
            int maxPerBinding,
            boolean once) {
        Instant horizon = now.plus(Duration.ofDays(SEARCH_DAYS));
        if (once && materializedLast != null) {
            return new WritePlan(List.of(), materializedLast, true);
        }
        if (generated >= maxPerBinding && materializedLast != null) {
            return new WritePlan(List.of(), materializedLast.plusSeconds(1), false);
        }
        Instant from = materializedLast != null ? materializedLast : cursorExclusive;
        List<Occurrence> more = preview(rule, window, from, 1);
        if (!more.isEmpty() && !more.get(0).occurrenceAt().isAfter(horizon)) {
            return new WritePlan(List.of(), more.get(0).occurrenceAt(), false);
        }
        Instant probe = materializedLast != null ? materializedLast : cursorExclusive;
        if (CalendarOccurrenceCalculator.preview(rule, probe, 1).isEmpty()) {
            return new WritePlan(List.of(), materializedLast, true);
        }
        return new WritePlan(List.of(), horizon, false);
    }

    static boolean allows(TimeWindow window, Instant occurrenceAt, CnMainlandWorkdayCalendar calendar) {
        ZonedDateTime local = occurrenceAt.atZone(ZONE);
        int second = local.toLocalTime().toSecondOfDay();
        for (int[] range : allowedRanges(local.toLocalDate(), window, calendar)) {
            if (second >= range[0] && second < range[1]) {
                return true;
            }
        }
        return false;
    }

    static List<int[]> allowedRanges(LocalDate day, TimeWindow window, CnMainlandWorkdayCalendar calendar) {
        List<int[]> covered = coveredRanges(day, window, calendar);
        if (window.mode() == TimeWindow.Mode.ALLOW) {
            return merge(covered);
        }
        return complement(merge(covered));
    }

    private static WritePlan deferredOrDone(Rule rule, Instant probe, Instant horizon, List<Occurrence> inWindow) {
        if (CalendarOccurrenceCalculator.preview(rule, probe, 1).isEmpty()) {
            Instant next = inWindow.isEmpty() ? null : inWindow.get(inWindow.size() - 1).occurrenceAt();
            return new WritePlan(inWindow, next, true);
        }
        return new WritePlan(inWindow, horizon, false);
    }

    private static List<Occurrence> previewByFiltering(
            Rule rule,
            TimeWindow window,
            CnMainlandWorkdayCalendar calendar,
            Instant afterExclusive,
            Instant horizon,
            int limit) {
        List<Occurrence> out = new ArrayList<>(limit);
        Instant cursor = afterExclusive;
        int guard = 0;
        while (out.size() < limit && guard++ < 20) {
            List<Occurrence> batch = CalendarOccurrenceCalculator.preview(rule, cursor, 100);
            if (batch.isEmpty()) {
                break;
            }
            Instant last = batch.get(batch.size() - 1).occurrenceAt();
            for (Occurrence occ : batch) {
                if (occ.occurrenceAt().isAfter(horizon)) {
                    return out;
                }
                if (allows(window, occ.occurrenceAt(), calendar)) {
                    out.add(occ);
                    if (out.size() == limit) {
                        return out;
                    }
                }
            }
            if (!last.isAfter(cursor)) {
                break;
            }
            cursor = last;
        }
        return out;
    }

    private static List<Occurrence> previewInterval(
            LocalDate startDate,
            LocalTime localTime,
            ZoneId zoneId,
            Duration step,
            TimeWindow window,
            CnMainlandWorkdayCalendar calendar,
            Instant afterExclusive,
            Instant horizon,
            int limit) {
        Instant anchor = CalendarOccurrenceCalculator.toUtc(startDate, localTime, zoneId);
        List<Occurrence> out = new ArrayList<>(limit);
        LocalDate day = afterExclusive.atZone(ZONE).toLocalDate().minusDays(1);
        LocalDate ruleStart = startDate.minusDays(1);
        if (ruleStart.isAfter(day)) {
            day = ruleStart;
        }
        LocalDate lastDay = horizon.atZone(ZONE).toLocalDate().plusDays(1);
        int guard = 0;
        while (out.size() < limit && !day.isAfter(lastDay) && guard++ < SEARCH_DAYS + 3) {
            for (int[] range : allowedRanges(day, window, calendar)) {
                Instant rangeStart = atSecond(day, range[0]);
                Instant rangeEnd = atSecond(day, range[1]);
                if (!rangeEnd.isAfter(afterExclusive)) {
                    continue;
                }
                Instant grid = ceilGrid(anchor, step, rangeStart, afterExclusive);
                while (grid != null
                        && grid.isBefore(rangeEnd)
                        && !grid.isAfter(horizon)
                        && out.size() < limit) {
                    if (grid.isAfter(afterExclusive)) {
                        out.add(CalendarOccurrenceCalculator.occurrence(grid));
                    }
                    grid = grid.plus(step);
                }
                if (out.size() >= limit) {
                    return out;
                }
            }
            day = day.plusDays(1);
        }
        return out;
    }

    private static Instant ceilGrid(Instant anchor, Duration step, Instant rangeStart, Instant afterExclusive) {
        Instant lower = rangeStart.isAfter(afterExclusive) ? rangeStart : afterExclusive.plusNanos(1);
        long stepSeconds = step.getSeconds();
        if (stepSeconds <= 0) {
            return null;
        }
        long delta = Duration.between(anchor, lower).getSeconds();
        if (delta < 0) {
            return anchor.isAfter(afterExclusive) ? anchor : null;
        }
        long k = (delta + stepSeconds - 1) / stepSeconds;
        Instant aligned = anchor.plus(step.multipliedBy(k));
        if (!aligned.isAfter(afterExclusive)) {
            aligned = aligned.plus(step);
        }
        return aligned;
    }

    private static Instant atSecond(LocalDate day, int secondOfDay) {
        if (secondOfDay >= DAY_SECONDS) {
            return day.plusDays(1).atStartOfDay(ZONE).toInstant();
        }
        return day.atTime(LocalTime.ofSecondOfDay(secondOfDay)).atZone(ZONE).toInstant();
    }

    private static List<int[]> coveredRanges(LocalDate day, TimeWindow window, CnMainlandWorkdayCalendar calendar) {
        List<int[]> ranges = new ArrayList<>();
        addSameDay(ranges, segmentsFor(day, window, calendar));
        addSpill(ranges, segmentsFor(day.minusDays(1), window, calendar));
        return ranges;
    }

    private static List<TimeWindow.Segment> segmentsFor(
            LocalDate day, TimeWindow window, CnMainlandWorkdayCalendar calendar) {
        return calendar.isWorkday(day) ? window.workdaySegments() : window.nonWorkdaySegments();
    }

    private static void addSameDay(List<int[]> ranges, List<TimeWindow.Segment> segments) {
        for (TimeWindow.Segment segment : segments) {
            int start = segment.startMinute() * 60;
            int end = segment.endMinute() * 60;
            if (end > start) {
                ranges.add(new int[] {start, end});
            } else {
                ranges.add(new int[] {start, DAY_SECONDS});
            }
        }
    }

    private static void addSpill(List<int[]> ranges, List<TimeWindow.Segment> segments) {
        for (TimeWindow.Segment segment : segments) {
            if (segment.endMinute() < segment.startMinute() && segment.endMinute() > 0) {
                ranges.add(new int[] {0, segment.endMinute() * 60});
            }
        }
    }

    private static List<int[]> merge(List<int[]> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        raw.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> out = new ArrayList<>();
        int start = raw.get(0)[0];
        int end = raw.get(0)[1];
        for (int i = 1; i < raw.size(); i++) {
            int[] next = raw.get(i);
            if (next[0] <= end) {
                end = Math.max(end, next[1]);
            } else {
                out.add(new int[] {start, end});
                start = next[0];
                end = next[1];
            }
        }
        out.add(new int[] {start, end});
        return out;
    }

    private static List<int[]> complement(List<int[]> covered) {
        List<int[]> out = new ArrayList<>();
        int cursor = 0;
        for (int[] range : covered) {
            if (range[0] > cursor) {
                out.add(new int[] {cursor, range[0]});
            }
            cursor = Math.max(cursor, range[1]);
        }
        if (cursor < DAY_SECONDS) {
            out.add(new int[] {cursor, DAY_SECONDS});
        }
        return out;
    }

    /** 避免预览在 Spring 容器外拿不到组件时重复构造失败。测试与适配器都走这一处。 */
    static final class CnMainlandWorkdayCalendarHolder {
        private static volatile CnMainlandWorkdayCalendar instance;

        private CnMainlandWorkdayCalendarHolder() {}

        static CnMainlandWorkdayCalendar get() {
            CnMainlandWorkdayCalendar local = instance;
            if (local == null) {
                local = new CnMainlandWorkdayCalendar();
                instance = local;
            }
            return local;
        }

        static void use(CnMainlandWorkdayCalendar calendar) {
            instance = calendar;
        }
    }
}
