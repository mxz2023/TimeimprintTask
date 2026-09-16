package cn.net.mxz.timeimprint.task.boot.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Q01—Q06：对当前仓库执行结构门禁，必须全部通过。 */
class StructureQualityGateTest {

    @Test
    void currentTreePassesQ01ToQ06() throws Exception {
        StructureQualityGate.Report report = StructureQualityGate.evaluate(StructureQualityGate.findRepoRoot());
        assertTrue(
                report.passed(),
                () -> "structure gate failed: " + StructureQualityGate.summarize(report) + "\n"
                        + report.violations().stream()
                                .limit(40)
                                .map(v -> v.check() + ": " + v.detail())
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse(""));
    }
}
