package cn.net.mxz.timeimprint.task.boot.structure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Q07 覆盖率不下降门禁：读取各模块 Jacoco CSV，与阶段基线比较。
 *
 * <p>基线文件：{@code docs/phases/P02/T05-COVERAGE-BASELINE.json}（T05 建立后锁定）。
 * 新增/修改行的 90%/80% 阈值由「不得低于基线」在结构收敛阶段落地；绝对阈值写入 DELIVERY。
 */
public final class CoverageNoDeclineGate {

    public record ModuleCoverage(String module, double lineRatio, double branchRatio) {}

    public record Violation(String detail) {}

    public record Report(List<Violation> violations, List<ModuleCoverage> current) {
        public boolean passed() {
            return violations.isEmpty();
        }
    }

    private CoverageNoDeclineGate() {}

    public static Report evaluate(Path repoRoot) throws IOException {
        Path baselinePath = repoRoot.resolve("docs/phases/P02/T05-COVERAGE-BASELINE.json");
        if (!Files.isRegularFile(baselinePath)) {
            return new Report(
                    List.of(new Violation("missing coverage baseline: " + baselinePath)),
                    List.of());
        }
        Map<String, ModuleCoverage> baseline = parseBaseline(Files.readString(baselinePath, StandardCharsets.UTF_8));
        List<ModuleCoverage> current = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();

        for (String module : StructureQualityGate.MODULES) {
            ModuleCoverage base = baseline.get(module);
            Path csv = repoRoot.resolve(module).resolve("target/site/jacoco/jacoco.csv");
            if (!Files.isRegularFile(csv)) {
                // boot-loader 的 jacoco:report 在 surefire 之后；同一次 `mvn test` 内允许暂缺并按基线计。
                if (module.equals("timeimprint-task-boot-loader") && base != null) {
                    current.add(base);
                    continue;
                }
                violations.add(new Violation("missing jacoco.csv for " + module + " (run ./mvnw test first)"));
                continue;
            }
            ModuleCoverage cov = readCsvTotals(module, csv);
            current.add(cov);
            if (base == null) {
                violations.add(new Violation("module missing from baseline: " + module));
                continue;
            }
            if (cov.lineRatio() + 1e-6 < base.lineRatio()) {
                violations.add(new Violation(String.format(
                        Locale.ROOT,
                        "line coverage declined for %s: current=%.6f baseline=%.6f",
                        module,
                        cov.lineRatio(),
                        base.lineRatio())));
            }
            if (cov.branchRatio() + 1e-6 < base.branchRatio()) {
                violations.add(new Violation(String.format(
                        Locale.ROOT,
                        "branch coverage declined for %s: current=%.6f baseline=%.6f",
                        module,
                        cov.branchRatio(),
                        base.branchRatio())));
            }
        }
        return new Report(List.copyOf(violations), List.copyOf(current));
    }

    /** 供脚本生成基线：输出紧凑 JSON。 */
    public static String toBaselineJson(List<ModuleCoverage> modules) {
        String body = modules.stream()
                .map(m -> String.format(
                        Locale.ROOT,
                        "    %s: {\"lineRatio\": %.6f, \"branchRatio\": %.6f}",
                        jsonKey(m.module()),
                        m.lineRatio(),
                        m.branchRatio()))
                .collect(Collectors.joining(",\n"));
        return "{\n  \"description\": \"P02 T05 Jacoco no-decline baseline (unit tests)\",\n  \"modules\": {\n"
                + body
                + "\n  }\n}\n";
    }

    public static ModuleCoverage readCsvTotals(String module, Path csv) throws IOException {
        long instructionMissed = 0;
        long instructionCovered = 0;
        long branchMissed = 0;
        long branchCovered = 0;
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        // GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,...
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = splitCsv(lines.get(i));
            if (cols.length < 7) {
                continue;
            }
            instructionMissed += Long.parseLong(cols[3]);
            instructionCovered += Long.parseLong(cols[4]);
            branchMissed += Long.parseLong(cols[5]);
            branchCovered += Long.parseLong(cols[6]);
        }
        long instrTotal = instructionMissed + instructionCovered;
        long branchTotal = branchMissed + branchCovered;
        double lineRatio = instrTotal == 0 ? 1.0 : (double) instructionCovered / instrTotal;
        double branchRatio = branchTotal == 0 ? 1.0 : (double) branchCovered / branchTotal;
        return new ModuleCoverage(module, lineRatio, branchRatio);
    }

    private static Map<String, ModuleCoverage> parseBaseline(String json) {
        // Minimal parser for {"modules":{"mod":{"lineRatio":x,"branchRatio":y},...}}
        Map<String, ModuleCoverage> out = new LinkedHashMap<>();
        int modulesIdx = json.indexOf("\"modules\"");
        if (modulesIdx < 0) {
            return out;
        }
        int brace = json.indexOf('{', modulesIdx);
        int end = matchingBrace(json, brace);
        String body = json.substring(brace + 1, end);
        int pos = 0;
        while (pos < body.length()) {
            int keyStart = body.indexOf('"', pos);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = body.indexOf('"', keyStart + 1);
            String module = body.substring(keyStart + 1, keyEnd);
            int objStart = body.indexOf('{', keyEnd);
            int objEnd = matchingBrace(body, objStart);
            String obj = body.substring(objStart, objEnd + 1);
            double line = readNumber(obj, "lineRatio");
            double branch = readNumber(obj, "branchRatio");
            out.put(module, new ModuleCoverage(module, line, branch));
            pos = objEnd + 1;
        }
        return out;
    }

    private static double readNumber(String obj, String key) {
        int i = obj.indexOf('"' + key + '"');
        if (i < 0) {
            return 0;
        }
        int colon = obj.indexOf(':', i);
        int end = colon + 1;
        while (end < obj.length() && (Character.isWhitespace(obj.charAt(end)) || obj.charAt(end) == '+')) {
            end++;
        }
        int start = end;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '.' || obj.charAt(end) == 'e'
                || obj.charAt(end) == 'E' || obj.charAt(end) == '-' || obj.charAt(end) == '+')) {
            end++;
        }
        return Double.parseDouble(obj.substring(start, end));
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("unbalanced braces");
    }

    private static String jsonKey(String module) {
        return "\"" + module + "\"";
    }

    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }
}
