package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * T07 · Kernel + Platform DDL zero-change assertion.
 *
 * Reads the SHA-256 baseline recorded in docs/phases/P01/BASELINE-SHA256.txt (section
 * "Kernel production sources" and "Platform public Flyway DDL") and verifies that every
 * listed file still has the same digest. Fails if any listed file is missing, changed,
 * or if new kernel/DDL files appear that are not in the baseline.
 *
 * Running this test after adding T07 fixtures proves the fixtures did not touch kernel
 * production sources or platform public DDL — satisfying the T07 integrity gate.
 */
class MxzT07KernelIntegrityTest {

    private static final String BASELINE_PATH = "docs/phases/P01/BASELINE-SHA256.txt";
    private static final String KERNEL_SRC     = "timeimprint-task-service-kernel/src/main/java";
    private static final String PLATFORM_DDL   = "timeimprint-task-service-storage-mysql/src/main/resources/db/migration/platform";

    @Test
    void kernelAndPlatformDdlMatchBaseline() throws Exception {
        Path repoRoot = findRepoRoot();

        // Parse baseline entries for the two sections
        List<BaselineEntry> baseline = parseBaseline(repoRoot.resolve(BASELINE_PATH));
        assertFalse(baseline.isEmpty(), "Baseline file must contain at least one kernel/DDL entry");

        // Verify all baseline entries still match current files
        for (BaselineEntry entry : baseline) {
            Path file = repoRoot.resolve(entry.path());
            assertTrue(Files.exists(file),
                    "Baseline file missing from working tree: " + entry.path());
            String actual = sha256Hex(Files.readAllBytes(file));
            assertEquals(entry.hash(), actual,
                    "SHA-256 mismatch for: " + entry.path()
                    + "\n  expected: " + entry.hash()
                    + "\n  actual:   " + actual);
        }

        // Verify no new files appeared in kernel/DDL directories that aren't in the baseline
        List<String> baselinePaths = baseline.stream().map(BaselineEntry::path).toList();
        List<String> currentKernel = listFiles(repoRoot.resolve(KERNEL_SRC));
        List<String> currentDdl    = listFiles(repoRoot.resolve(PLATFORM_DDL));

        for (String kFile : currentKernel) {
            assertTrue(baselinePaths.contains(kFile),
                    "New kernel source file not in baseline: " + kFile
                    + " — update docs/phases/P01/BASELINE-SHA256.txt and validate the change.");
        }
        for (String dFile : currentDdl) {
            assertTrue(baselinePaths.contains(dFile),
                    "New platform DDL file not in baseline: " + dFile
                    + " — update docs/phases/P01/BASELINE-SHA256.txt and validate the change.");
        }
    }

    private static Path findRepoRoot() {
        Path p = Path.of("").toAbsolutePath();
        // When run from the module directory or reactor, walk up until we find the parent pom
        while (p != null && !Files.exists(p.resolve(BASELINE_PATH))) {
            p = p.getParent();
        }
        if (p == null || !Files.exists(p.resolve(BASELINE_PATH))) {
            throw new IllegalStateException("Cannot locate repository root (expected " + BASELINE_PATH + ")");
        }
        return p;
    }

    /** Parse entries from the "Kernel production sources" and "Platform public Flyway DDL" sections. */
    private static List<BaselineEntry> parseBaseline(Path baselineFile) throws IOException {
        List<String> lines = Files.readAllLines(baselineFile);
        boolean inSection = false;
        List<BaselineEntry> entries = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("## Kernel production sources") || line.startsWith("## Platform public Flyway DDL")) {
                inSection = true;
                continue;
            }
            if (inSection && line.startsWith("## ")) {
                // Next section started
                inSection = false;
                continue;
            }
            if (inSection && !line.isBlank() && !line.startsWith("#")) {
                // Format: <64-hex>  <path>
                String trimmed = line.trim();
                int spaceIdx = trimmed.indexOf("  ");
                if (spaceIdx == 64) {
                    String hash = trimmed.substring(0, 64);
                    String path = trimmed.substring(66).trim();
                    entries.add(new BaselineEntry(hash, path));
                }
            }
        }
        return entries;
    }

    private static List<String> listFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            Path root = findRepoRoot();
            return s.filter(Files::isRegularFile)
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    record BaselineEntry(String hash, String path) {}
}
