package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 确认批准的平级模块目录存在（含 P06 identity 与 P09 scenario-deadline）。
 */
class ModuleBaselineTest {

    private static final List<String> MODULES = List.of(
            "timeimprint-task-common",
            "timeimprint-task-domain",
            "timeimprint-task-service-kernel",
            "timeimprint-task-service-extension-api",
            "timeimprint-task-service-application",
            "timeimprint-task-service-runtime",
            "timeimprint-task-service-storage-mysql",
            "timeimprint-task-service-capability-calendar",
            "timeimprint-task-adapter",
            "timeimprint-task-identity",
            "timeimprint-task-service-capability-notification",
            "timeimprint-task-service-scenario-basic",
            "timeimprint-task-service-scenario-deadline",
            "timeimprint-task-gateway",
            "timeimprint-task-web",
            "timeimprint-task-boot-loader");

    @Test
    void approvedModulesExist() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("pom.xml"))) {
            root = root.getParent();
        }
        // Surefire cwd is module dir; parent is repo root for boot-loader when run from reactor,
        // but when run alone climb to aggregator.
        Path probe = Path.of("").toAbsolutePath();
        if (!Files.exists(probe.resolve("timeimprint-task-common"))) {
            probe = probe.getParent();
        }
        for (String module : MODULES) {
            assertTrue(Files.isDirectory(probe.resolve(module)), () -> "missing module " + module);
        }
    }
}
