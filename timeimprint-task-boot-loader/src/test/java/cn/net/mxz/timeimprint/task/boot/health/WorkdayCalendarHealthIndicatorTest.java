package cn.net.mxz.timeimprint.task.boot.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CnMainlandWorkdayCalendar;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class WorkdayCalendarHealthIndicatorTest {

    @Test
    void fileErrorsDownValidUpAndDecemberWarns() {
        var down = WorkdayCalendarHealthIndicator.toHealth(
                CnMainlandWorkdayCalendar.withErrors(List.of("2026.json 不合法")), LocalDate.parse("2026-10-18"));
        assertEquals(Status.DOWN, down.getStatus());

        CnMainlandWorkdayCalendar calendar = new CnMainlandWorkdayCalendar();
        var up = new WorkdayCalendarHealthIndicator(calendar).health();
        assertEquals(Status.UP, up.getStatus());

        var december = WorkdayCalendarHealthIndicator.toHealth(calendar, LocalDate.parse("2026-12-01"));
        assertEquals(Status.UP, december.getStatus());
        assertTrue(december.getDetails().get("warning").toString().contains("2027"));
    }
}
