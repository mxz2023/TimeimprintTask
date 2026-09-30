package cn.net.mxz.timeimprint.task.boot.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Q07 负例：故意压低基线对比值时，门禁必须失败。 */
class CoverageNoDeclineGateNegativeTest {

    @TempDir
    Path temp;

    @Test
    void detectsLineCoverageDecline() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo.resolve("docs/phases/P02"));
        // Inflated baseline so current (synthetic low) declines
        Files.writeString(
                repo.resolve("docs/phases/P02/T05-COVERAGE-BASELINE.json"),
                """
                {
                  "description": "synthetic",
                  "modules": {
                    "timeimprint-task-common": {"lineRatio": 0.990000, "branchRatio": 0.990000}
                  }
                }
                """);
        for (String module : StructureQualityGate.MODULES) {
            Path csvDir = repo.resolve(module).resolve("target/site/jacoco");
            Files.createDirectories(csvDir);
            // Header + one class with low coverage
            Files.writeString(
                    csvDir.resolve("jacoco.csv"),
                    """
                    GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
                    g,p,C,90,10,8,2,0,0,0,0,0,0
                    """);
        }
        CoverageNoDeclineGate.Report report = CoverageNoDeclineGate.evaluate(repo);
        assertFalse(report.passed());
        assertTrue(
                report.violations().stream().anyMatch(v -> v.detail().contains("line coverage declined")),
                () -> report.violations().toString());
    }
}
