package cn.net.mxz.timeimprint.task.boot.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * J02 / J10: production sources must not import Jackson 2 databind/core/datatype.
 * Flyway may still pull Jackson 2 transitively until upstream migrates (03 §1.1).
 */
class JacksonMigrationPurityGateTest {

    @Test
    void productionSourcesDoNotImportJackson2DatabindCoreOrDatatype() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("timeimprint-task-boot-loader"))) {
            root = root.getParent();
        }
        List<String> offenders = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            for (Path java : walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList()) {
                for (String line : Files.readAllLines(java, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    if (trimmed.startsWith("import com.fasterxml.jackson.annotation")) {
                        continue;
                    }
                    if (trimmed.startsWith("import com.fasterxml.jackson.databind")
                            || trimmed.startsWith("import com.fasterxml.jackson.core")
                            || trimmed.startsWith("import com.fasterxml.jackson.datatype")) {
                        offenders.add(root.relativize(java) + ": " + trimmed);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Jackson 2 imports remain:\n" + String.join("\n", offenders));
    }
}
