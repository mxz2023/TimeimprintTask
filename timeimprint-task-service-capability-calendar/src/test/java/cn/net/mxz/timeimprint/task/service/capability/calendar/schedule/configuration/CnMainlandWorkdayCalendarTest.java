package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CnMainlandWorkdayCalendarTest {

    private final CnMainlandWorkdayCalendar calendar = new CnMainlandWorkdayCalendar();

    @Test
    void classifies2026OfficialDaysAndFallsBackLaterYears() {
        assertTrue(calendar.fileErrors().isEmpty(), calendar.fileErrors().toString());
        assertFalse(calendar.isWorkday(LocalDate.parse("2026-01-01")));
        assertTrue(calendar.isWorkday(LocalDate.parse("2026-01-04")));
        assertTrue(calendar.isWorkday(LocalDate.parse("2026-01-05")));
        assertFalse(calendar.isWorkday(LocalDate.parse("2026-01-10")));
        assertFalse(calendar.isWorkday(LocalDate.parse("2026-10-01")));
        assertTrue(calendar.isWorkday(LocalDate.parse("2026-09-20")));
        assertTrue(calendar.isWorkday(LocalDate.parse("2027-01-04")));
        assertFalse(calendar.isWorkday(LocalDate.parse("2027-01-02")));
        assertTrue(calendar.warnings(LocalDate.parse("2026-12-01")).stream().anyMatch(w -> w.contains("2027")));
        assertTrue(calendar.warnings(LocalDate.parse("2026-10-18")).isEmpty());
    }

    @Test
    void rejectsIllegalYearFiles() {
        assertNotNull(CnMainlandWorkdayCalendar.validate("2026.json", 2026, json("""
                {"region":"CN_MAINLAND","year":2026,"source":"x","holidays":[],"workdays":["2026-01-05"]}
                """)));
        assertNotNull(CnMainlandWorkdayCalendar.validate("2026.json", 2026, json("""
                {"region":"CN_MAINLAND","year":2026,"source":"x","holidays":["2026-01-01"],"workdays":["2026-01-01"]}
                """)));
        assertNotNull(CnMainlandWorkdayCalendar.validate("2026.json", 2026, json("""
                {"region":"CN_MAINLAND","year":2027,"source":"x","holidays":[],"workdays":[]}
                """)));
        assertNotNull(CnMainlandWorkdayCalendar.validate("2026.json", 2026, json("""
                {"region":"CN_MAINLAND","year":2026,"holidays":[],"workdays":[]}
                """)));
        assertNotNull(CnMainlandWorkdayCalendar.validate("2026.json", 2026, json("""
                {"region":"CN_MAINLAND","year":2026,"source":"x","holidays":["2026-01-01","2026-01-01"],"workdays":[]}
                """)));
        List<String> errors = new ArrayList<>();
        var years = new LinkedHashMap<Integer, CnMainlandWorkdayCalendar.YearData>();
        byte[] body = """
                {"region":"CN_MAINLAND","year":2026,"source":"x","holidays":[],"workdays":[]}
                """.getBytes(StandardCharsets.UTF_8);
        CnMainlandWorkdayCalendar.parseFile("2026.json", new ByteArrayInputStream(body), years, errors);
        CnMainlandWorkdayCalendar.parseFile("2026.json", new ByteArrayInputStream(body), years, errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("重复")));
    }

    private static JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }
}
