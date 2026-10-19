package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 中国大陆法定工作日。数据来自 {@code calendar/cn-mainland-workday/{yyyy}.json}。
 * 缺年份按周一至周五回退，并记一次日志。文件不合法时 {@link #fileErrors()} 非空，readiness 应拒绝。
 */
@Component
public class CnMainlandWorkdayCalendar {

    private static final Logger log = Logger.getLogger(CnMainlandWorkdayCalendar.class.getName());
    private static final Pattern FILE_YEAR = Pattern.compile("(\\d{4})\\.json$");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> KEYS = Set.of("region", "year", "source", "holidays", "workdays");

    private final Map<Integer, YearData> years;
    private final List<String> errors;
    private final Set<Integer> loggedMissing = ConcurrentHashMap.newKeySet();

    public CnMainlandWorkdayCalendar() {
        LoadResult loaded = loadClasspath();
        this.years = loaded.years();
        this.errors = loaded.errors();
    }

    private CnMainlandWorkdayCalendar(Map<Integer, YearData> years, List<String> errors) {
        this.years = years;
        this.errors = errors;
    }

    public static CnMainlandWorkdayCalendar withErrors(List<String> errors) {
        return new CnMainlandWorkdayCalendar(Map.of(), List.copyOf(errors));
    }

    public List<String> fileErrors() {
        return errors;
    }

    public boolean hasYear(int year) {
        return years.containsKey(year);
    }

    public boolean isWorkday(LocalDate date) {
        YearData data = years.get(date.getYear());
        if (data == null) {
            noteMissing(date.getYear());
            return date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
        }
        if (data.holidays().contains(date)) {
            return false;
        }
        if (data.workdays().contains(date)) {
            return true;
        }
        return date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
    }

    /** 缺少年份，以及 12 月 1 日起次年文件仍缺。不表示 readiness 失败。 */
    public List<String> warnings(LocalDate today) {
        List<String> out = new ArrayList<>();
        if (!years.containsKey(today.getYear())) {
            out.add("缺少 " + today.getYear() + " 年中国大陆工作日数据，该年按周一至周五回退");
        }
        if (today.getMonthValue() == 12 && !years.containsKey(today.getYear() + 1)) {
            out.add("12 月 1 日起仍缺少 " + (today.getYear() + 1) + " 年中国大陆工作日数据");
        }
        return List.copyOf(out);
    }

    private void noteMissing(int year) {
        if (loggedMissing.add(year)) {
            log.log(Level.WARNING, "缺少 {0} 年中国大陆工作日数据，该年按周一至周五回退", String.valueOf(year));
        }
    }

    static LoadResult loadClasspath() {
        Map<Integer, YearData> years = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:calendar/cn-mainland-workday/*.json");
            for (Resource resource : resources) {
                String name = resource.getFilename() == null ? "" : resource.getFilename();
                try (InputStream in = resource.getInputStream()) {
                    parseFile(name, in, years, errors);
                } catch (IOException ex) {
                    errors.add(name + " 无法读取");
                }
            }
        } catch (IOException ex) {
            errors.add("无法扫描 calendar/cn-mainland-workday");
        }
        if (years.isEmpty() && errors.isEmpty()) {
            errors.add("未找到 calendar/cn-mainland-workday 数据文件");
        }
        return new LoadResult(Map.copyOf(years), List.copyOf(errors));
    }

    static void parseFile(String filename, InputStream in, Map<Integer, YearData> years, List<String> errors) {
        Matcher matcher = FILE_YEAR.matcher(filename);
        if (!matcher.find()) {
            errors.add(filename + " 文件名不是 yyyy.json");
            return;
        }
        int fileYear = Integer.parseInt(matcher.group(1));
        JsonNode root;
        try {
            root = JSON.readTree(in);
        } catch (RuntimeException ex) {
            errors.add(filename + " 不是合法 JSON");
            return;
        }
        String error = validate(filename, fileYear, root);
        if (error != null) {
            errors.add(error);
            return;
        }
        Set<LocalDate> holidays = readDates(root.get("holidays"));
        Set<LocalDate> workdays = readDates(root.get("workdays"));
        if (years.containsKey(fileYear)) {
            errors.add(filename + " 与另一份 " + fileYear + " 年文件重复");
            return;
        }
        years.put(fileYear, new YearData(holidays, workdays));
    }

    static String validate(String filename, int fileYear, JsonNode root) {
        if (root == null || !root.isObject()) {
            return filename + " 必须是 JSON 对象";
        }
        List<String> names = new ArrayList<>();
        root.propertyNames().forEach(names::add);
        if (names.size() != KEYS.size() || !KEYS.containsAll(names)) {
            return filename + " 字段必须正好是 region、year、source、holidays、workdays";
        }
        if (!root.get("region").isString() || !"CN_MAINLAND".equals(root.get("region").asString())) {
            return filename + " 的 region 必须是 CN_MAINLAND";
        }
        if (!root.get("year").isIntegralNumber() || root.get("year").asInt() != fileYear) {
            return filename + " 的 year 必须与文件名一致";
        }
        if (!root.get("source").isString() || root.get("source").asString().isBlank()) {
            return filename + " 的 source 不能为空";
        }
        String holidayError = validateDates(filename, "holidays", fileYear, root.get("holidays"), false);
        if (holidayError != null) {
            return holidayError;
        }
        String workdayError = validateDates(filename, "workdays", fileYear, root.get("workdays"), true);
        if (workdayError != null) {
            return workdayError;
        }
        Set<LocalDate> holidays = readDates(root.get("holidays"));
        for (LocalDate workday : readDates(root.get("workdays"))) {
            if (holidays.contains(workday)) {
                return filename + " 的 holidays 与 workdays 相交：" + workday;
            }
        }
        return null;
    }

    private static String validateDates(String filename, String field, int year, JsonNode node, boolean weekendOnly) {
        if (node == null || !node.isArray()) {
            return filename + " 的 " + field + " 必须是数组";
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isString()) {
                return filename + " 的 " + field + " 必须是日期字符串";
            }
            String text = item.asString();
            if (!seen.add(text)) {
                return filename + " 的 " + field + " 有重复日期 " + text;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(text);
            } catch (DateTimeParseException ex) {
                return filename + " 的 " + field + " 含非法日期 " + text;
            }
            if (date.getYear() != year) {
                return filename + " 的 " + field + " 含不在 " + year + " 年的日期 " + text;
            }
            if (weekendOnly) {
                DayOfWeek dow = date.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    return filename + " 的 workdays 只能是周六或周日：" + text;
                }
            }
        }
        return null;
    }

    private static Set<LocalDate> readDates(JsonNode node) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (JsonNode item : node) {
            dates.add(LocalDate.parse(item.asString()));
        }
        return Set.copyOf(dates);
    }

    record YearData(Set<LocalDate> holidays, Set<LocalDate> workdays) {}

    record LoadResult(Map<Integer, YearData> years, List<String> errors) {}
}
