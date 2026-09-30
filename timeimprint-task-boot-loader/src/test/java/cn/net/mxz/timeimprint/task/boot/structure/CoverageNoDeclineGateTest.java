package cn.net.mxz.timeimprint.task.boot.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Q07：各模块 Jacoco 指令/分支覆盖率不得低于 P02 T05 基线。
 *
 * <p>在 reactor 末尾的 boot-loader 单元测试中执行；依赖上游模块已写出
 * {@code target/site/jacoco/jacoco.csv}。boot-loader 自身报告若尚未生成则按基线计。
 */
class CoverageNoDeclineGateTest {

    @Test
    void moduleCoverageMustNotDeclineAgainstBaseline() throws Exception {
        Path root = StructureQualityGate.findRepoRoot();
        Path baseline = root.resolve("docs/phases/P02/T05-COVERAGE-BASELINE.json");
        assertTrue(Files.isRegularFile(baseline), "missing " + baseline);
        CoverageNoDeclineGate.Report report = CoverageNoDeclineGate.evaluate(root);
        assertTrue(
                report.passed(),
                () -> report.violations().stream()
                        .map(CoverageNoDeclineGate.Violation::detail)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("unknown"));
    }
}
