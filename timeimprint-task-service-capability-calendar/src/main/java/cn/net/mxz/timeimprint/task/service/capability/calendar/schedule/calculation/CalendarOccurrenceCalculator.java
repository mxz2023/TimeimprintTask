package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.calculation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 五种公历规则的纯算法实现；预览与 Planner 共用。
 */
public final class CalendarOccurrenceCalculator {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OCCURRENCE_KEY =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private CalendarOccurrenceCalculator() {}

    public record Occurrence(String occurrenceKey, Instant occurrenceAt) {}

    public sealed interface Rule permits Once, Daily, Weekly, Monthly, EveryNDays {}

    public record Once(LocalDate localDate, LocalTime localTime, ZoneId zoneId) implements Rule {}

    public record Daily(LocalDate startDate, LocalTime localTime, ZoneId zoneId) implements Rule {}

    public record Weekly(LocalDate startDate, int weekday, LocalTime localTime, ZoneId zoneId)
            implements Rule {}

    public record Monthly(LocalDate startDate, int dayOfMonth, LocalTime localTime, ZoneId zoneId)
            implements Rule {}

    public record EveryNDays(LocalDate startDate, int intervalDays, LocalTime localTime, ZoneId zoneId)
            implements Rule {}

    public static List<Occurrence> preview(Rule rule, Instant afterExclusive, int limit) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(afterExclusive, "afterExclusive");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be 1..100");
        }
        return switch (rule) {
            case Once once -> previewOnce(once, afterExclusive, limit);
            case Daily daily -> previewDaily(daily, afterExclusive, limit);
            case Weekly weekly -> previewWeekly(weekly, afterExclusive, limit);
            case Monthly monthly -> previewMonthly(monthly, afterExclusive, limit);
            case EveryNDays every -> previewEveryNDays(every, afterExclusive, limit);
        };
    }

    private static List<Occurrence> previewOnce(Once rule, Instant after, int limit) {
        requireZone(rule.zoneId());
        Instant at = toUtc(rule.localDate(), rule.localTime(), rule.zoneId());
        List<Occurrence> out = new ArrayList<>(1);
        if (at.isAfter(after) && limit > 0) {
            out.add(occurrence(at));
        }
        return out;
    }

    private static List<Occurrence> previewDaily(Daily rule, Instant after, int limit) {
        requireZone(rule.zoneId());
        List<Occurrence> out = new ArrayList<>(limit);
        LocalDate day = rule.startDate();
        // Advance until at/after window efficiently
        ZonedDateTime afterLocal = after.atZone(rule.zoneId());
        if (day.isBefore(afterLocal.toLocalDate())) {
            day = afterLocal.toLocalDate();
        }
        int guard = 0;
        while (out.size() < limit && guard++ < 100000) {
            Instant at = toUtc(day, rule.localTime(), rule.zoneId());
            if (!at.isAfter(after)) {
                day = day.plusDays(1);
                continue;
            }
            if (day.isBefore(rule.startDate())) {
                day = day.plusDays(1);
                continue;
            }
            out.add(occurrence(at));
            day = day.plusDays(1);
        }
        return out;
    }

    private static List<Occurrence> previewWeekly(Weekly rule, Instant after, int limit) {
        requireZone(rule.zoneId());
        if (rule.weekday() < 1 || rule.weekday() > 7) {
            throw new IllegalArgumentException("weekday must be 1..7");
        }
        List<Occurrence> out = new ArrayList<>(limit);
        LocalDate day = rule.startDate();
        int guard = 0;
        while (out.size() < limit && guard++ < 100000) {
            if (day.getDayOfWeek().getValue() != rule.weekday() || day.isBefore(rule.startDate())) {
                day = day.plusDays(1);
                continue;
            }
            Instant at = toUtc(day, rule.localTime(), rule.zoneId());
            if (at.isAfter(after)) {
                out.add(occurrence(at));
            }
            day = day.plusDays(1);
        }
        return out;
    }

    private static List<Occurrence> previewMonthly(Monthly rule, Instant after, int limit) {
        requireZone(rule.zoneId());
        if (rule.dayOfMonth() < 1 || rule.dayOfMonth() > 31) {
            throw new IllegalArgumentException("dayOfMonth must be 1..31");
        }
        List<Occurrence> out = new ArrayList<>(limit);
        LocalDate cursor = rule.startDate().withDayOfMonth(1);
        int guard = 0;
        while (out.size() < limit && guard++ < 100000) {
            LocalDate day = clampDayOfMonth(cursor, rule.dayOfMonth());
            if (!day.isBefore(rule.startDate())) {
                Instant at = toUtc(day, rule.localTime(), rule.zoneId());
                if (at.isAfter(after)) {
                    out.add(occurrence(at));
                }
            }
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    private static List<Occurrence> previewEveryNDays(EveryNDays rule, Instant after, int limit) {
        requireZone(rule.zoneId());
        if (rule.intervalDays() < 1 || rule.intervalDays() > 3650) {
            throw new IllegalArgumentException("intervalDays must be 1..3650");
        }
        List<Occurrence> out = new ArrayList<>(limit);
        LocalDate day = rule.startDate();
        // Jump near after
        Instant startAt = toUtc(rule.startDate(), rule.localTime(), rule.zoneId());
        if (!after.isBefore(startAt)) {
            long daysAfter = java.time.Duration.between(startAt, after).toDays();
            long steps = Math.max(0, daysAfter / rule.intervalDays());
            day = rule.startDate().plusDays(steps * rule.intervalDays());
        }
        int guard = 0;
        while (out.size() < limit && guard++ < 100000) {
            Instant at = toUtc(day, rule.localTime(), rule.zoneId());
            if (at.isAfter(after) && !day.isBefore(rule.startDate())) {
                out.add(occurrence(at));
            }
            day = day.plusDays(rule.intervalDays());
        }
        return out;
    }

    static LocalDate clampDayOfMonth(LocalDate monthStart, int dayOfMonth) {
        int last = monthStart.lengthOfMonth();
        return monthStart.withDayOfMonth(Math.min(dayOfMonth, last));
    }

    static Instant toUtc(LocalDate date, LocalTime time, ZoneId zoneId) {
        return ZonedDateTime.of(LocalDateTime.of(date, time), zoneId).toInstant();
    }

    static Occurrence occurrence(Instant at) {
        Instant truncated = at.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String key = OCCURRENCE_KEY.format(truncated.atZone(ZoneId.of("UTC")));
        return new Occurrence(key, truncated);
    }

    private static void requireZone(ZoneId zoneId) {
        if (!BUSINESS_ZONE.equals(zoneId)) {
            throw new IllegalArgumentException("zoneId must be Asia/Shanghai in P01");
        }
    }
}
