package cn.net.mxz.timeimprint.task.boot.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T05 负例：故意构造违规树，证明门禁会对 Q01—Q04 产生失败结果（不只依赖人工观察）。
 */
class StructureQualityGateNegativeTest {

    @TempDir
    Path temp;

    @Test
    void detectsForbiddenImplPackageAndMissingOwner() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
                "timeimprint-task-domain/src/main/java/cn/net/mxz/timeimprint/task/domain/impl/BadThing.java",
                """
                package cn.net.mxz.timeimprint.task.domain.impl;
                public final class BadThing {}
                """);
        files.put(
                "timeimprint-task-domain/src/main/java/cn/net/mxz/timeimprint/task/domain/shared/view/OrphanView.java",
                """
                package cn.net.mxz.timeimprint.task.domain.shared.view;
                public record OrphanView(String id) {}
                """);
        // path mismatch: declares shared.view but file under wrong path already covered by impl;
        // missing owner for OrphanView is enough for Q04
        StructureQualityGate.Report report = StructureQualityGate.evaluateSynthetic(temp, files);
        assertFalse(report.passed(), "synthetic violations must fail the gate");
        assertTrue(
                report.of(StructureQualityGate.Check.Q01_PACKAGE_SHAPE).stream()
                        .anyMatch(v -> v.detail().contains("impl") || v.detail().contains("forbidden")),
                () -> report.of(StructureQualityGate.Check.Q01_PACKAGE_SHAPE).toString());
        assertTrue(
                report.of(StructureQualityGate.Check.Q04_OWNER_MAPPING).stream()
                        .anyMatch(v -> v.detail().contains("OrphanView")),
                () -> report.of(StructureQualityGate.Check.Q04_OWNER_MAPPING).toString());
    }

    @Test
    void detectsPathPackageMismatch() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
                "timeimprint-task-common/src/main/java/cn/net/mxz/timeimprint/task/common/time/WrongPlace.java",
                """
                package cn.net.mxz.timeimprint.task.common.hashing;
                public final class WrongPlace {}
                """);
        StructureQualityGate.Report report = StructureQualityGate.evaluateSynthetic(temp, files);
        assertFalse(report.passed());
        assertTrue(
                report.of(StructureQualityGate.Check.Q02_PATH_PACKAGE_XML).stream()
                        .anyMatch(v -> v.detail().contains("path/package mismatch")),
                () -> report.violations().toString());
    }

    @Test
    void detectsEmptyOwnerAssertion() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
                "timeimprint-task-common/src/main/java/cn/net/mxz/timeimprint/task/common/time/EmptyOwned.java",
                """
                package cn.net.mxz.timeimprint.task.common.time;
                public final class EmptyOwned {}
                """);
        files.put(
                "timeimprint-task-common/src/test/java/cn/net/mxz/timeimprint/task/common/time/EmptyOwnedTest.java",
                """
                package cn.net.mxz.timeimprint.task.common.time;
                import org.junit.jupiter.api.Test;
                class EmptyOwnedTest {
                    @Test
                    void noop() {
                        new EmptyOwned();
                    }
                }
                """);
        StructureQualityGate.Report report = StructureQualityGate.evaluateSynthetic(temp, files);
        assertFalse(report.passed());
        assertTrue(
                report.of(StructureQualityGate.Check.Q05_OWNER_ASSERTION).stream()
                        .anyMatch(v -> v.detail().contains("EmptyOwnedTest")),
                () -> report.violations().toString());
    }

    @Test
    void detectsUnsupportedTestSuffix() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
                "timeimprint-task-common/src/main/java/cn/net/mxz/timeimprint/task/common/time/Named.java",
                """
                package cn.net.mxz.timeimprint.task.common.time;
                public final class Named {}
                """);
        files.put(
                "timeimprint-task-common/src/test/java/cn/net/mxz/timeimprint/task/common/time/Named.java",
                """
                package cn.net.mxz.timeimprint.task.common.time;
                class Named {}
                """);
        StructureQualityGate.Report report = StructureQualityGate.evaluateSynthetic(temp, files);
        assertFalse(report.passed());
        assertTrue(
                report.of(StructureQualityGate.Check.Q03_TEST_MIRROR).stream()
                        .anyMatch(v -> v.detail().contains("unsupported test suffix")),
                () -> report.violations().toString());
    }

    @Test
    void tempDirIsWritable() throws Exception {
        assertTrue(Files.isDirectory(temp));
    }
}
