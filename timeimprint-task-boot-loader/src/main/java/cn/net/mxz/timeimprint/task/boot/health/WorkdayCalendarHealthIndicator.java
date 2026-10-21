package cn.net.mxz.timeimprint.task.boot.health;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CnMainlandWorkdayCalendar;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 中国大陆工作日文件不合法时 readiness 不就绪。缺年份只记警告，状态仍为 UP。
 */
@Component("workdayCalendar")
public class WorkdayCalendarHealthIndicator implements HealthIndicator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CnMainlandWorkdayCalendar calendar;

    public WorkdayCalendarHealthIndicator(CnMainlandWorkdayCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public Health health() {
        return toHealth(calendar, LocalDate.now(ZONE));
    }

    static Health toHealth(CnMainlandWorkdayCalendar calendar, LocalDate today) {
        if (!calendar.fileErrors().isEmpty()) {
            return Health.down().withDetail("workdayFiles", calendar.fileErrors()).build();
        }
        List<String> warnings = calendar.warnings(today);
        Health.Builder builder = Health.up();
        if (!warnings.isEmpty()) {
            builder.withDetail("warning", warnings);
        }
        return builder.build();
    }
}
