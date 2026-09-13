package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.boot.it.support.MxzClaimAndHoldMain;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.runtime.MxzRuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 07 §6 独立接管真时钟 SLA：子进程领取后被杀，父进程须在原租约到期后 {@code 2 × scan interval + 10s}
 * 内完成接管并只产生一份本地效果；禁止人为把 {@code lease_until} 改到过去。
 *
 * <p>effective scan interval = Worker/Reaper {@code fixedDelay} 2000ms（与当前生产调度一致）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("dual-process-it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MxzTakeoverSlaDualProcessIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String JDBC =
            "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC";
    private static final String DB_USER = "tit";
    private static final String DB_PASS = "tit_local";

    /** Matches {@link cn.net.mxz.timeimprint.task.service.runtime.MxzActionWorker} / Reaper fixedDelay. */
    private static final long SCAN_INTERVAL_MS = 2000L;
    /** 03 下限；缩短真挂钟等待。 */
    private static final int LEASE_SECONDS = 10;
    private static final Duration SLA_AFTER_LEASE =
            Duration.ofMillis(2 * SCAN_INTERVAL_MS).plusSeconds(10);

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MxzSignalProcessingService signalProcessing;

    @Autowired
    ActionJobExecutionPort actionPort;

    @Autowired
    TransactionBoundary tx;

    @Autowired
    MxzRuntimeAdmission admission;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> JDBC);
        r.add("spring.datasource.username", () -> DB_USER);
        r.add("spring.datasource.password", () -> DB_PASS);
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
        r.add("INSTANCE_ID", () -> "sla-parent-" + UUID.randomUUID());
        r.add("server.address", () -> "127.0.0.1");
        // Parent must run scheduled reaper + ActionWorker; mysql-it defaults this to false.
        r.add("spring.task.scheduling.enabled", () -> "true");
    }

    @AfterEach
    void stopClaims() {
        admission.suspendClaimsForTests();
    }

    @Test
    void takeoverWithinTwoScanIntervalsPlusTenSeconds() throws Exception {
        admission.resetForTests();
        // Stop parent workers from racing the child JDBC claim; HTTP writes still allowed.
        admission.suspendClaimsForTests();

        // Keep dirty-queue READY jobs from starving the scheduled poller during the SLA window.
        // Must stay before expires_at (chk_action_expires_at); 5 minutes covers lease+SLA budget.
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  next_attempt_at = UTC_TIMESTAMP() + INTERVAL 5 MINUTE,
                  available_at = UTC_TIMESTAMP() + INTERVAL 5 MINUTE
                WHERE status IN ('READY', 'RETRY_WAIT')
                  AND (expires_at IS NULL OR expires_at > UTC_TIMESTAMP() + INTERVAL 5 MINUTE)
                """);

        long definitionId = createOnceReminder("SLA dual " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ?",
                Long.class,
                definitionId,
                instanceId);
        signalProcessing.processSignal(signalId);

        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE definition_id = ? AND status = 'READY' LIMIT 1",
                Long.class,
                definitionId);
        assertNotNull(actionJobId);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND
                WHERE action_job_id = ?
                """,
                actionJobId);

        Path tokenFile = Files.createTempFile("sla-token-", ".txt");
        assertEquals("READY", statusOfAction(actionJobId), "precondition: action must be READY for child claim");
        Process child = startClaimHold(actionJobId, tokenFile, LEASE_SECONDS);
        try {
            String oldToken = awaitToken(tokenFile, Duration.ofSeconds(20), child, actionJobId);
            assertEquals("RUNNING", statusOfAction(actionJobId));

            Timestamp leaseTs = jdbc.queryForObject(
                    "SELECT lease_until FROM tt_action_job WHERE action_job_id = ?",
                    Timestamp.class,
                    actionJobId);
            assertNotNull(leaseTs);
            Instant leaseUntil = leaseTs.toInstant();
            Instant deadline = leaseUntil.plus(SLA_AFTER_LEASE);

            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));

            // Surviving parent resumes scheduled reaper + worker (true wall-clock takeover).
            admission.resetForTests();

            Instant doneAt = awaitSucceeded(actionJobId, deadline);
            assertTrue(
                    !doneAt.isAfter(deadline),
                    "takeover after lease expiry must be ≤ 2×scan("
                            + SCAN_INTERVAL_MS
                            + "ms)+10s; leaseUntil="
                            + leaseUntil
                            + " doneAt="
                            + doneAt
                            + " deadline="
                            + deadline
                            + " elapsedAfterLease="
                            + Duration.between(leaseUntil, doneAt));

            Integer inbox = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?", Integer.class, instanceId);
            assertEquals(1, inbox, "exactly one local effect");

            Boolean oldCas = tx.execute(
                    () -> actionPort.completeWithToken(
                            actionJobId, oldToken, "SUCCEEDED", "STALE", "should fail", Instant.now()));
            assertEquals(Boolean.FALSE, oldCas, "old token CAS must be 0");
        } finally {
            child.destroyForcibly();
            Files.deleteIfExists(tokenFile);
        }
    }

    private Instant awaitSucceeded(long actionJobId, Instant deadline) throws InterruptedException {
        while (true) {
            Instant now = Instant.now();
            String status = statusOfAction(actionJobId);
            if ("SUCCEEDED".equals(status)) {
                return now;
            }
            if (now.isAfter(deadline.plusSeconds(2))) {
                throw new AssertionError(
                        "Action not SUCCEEDED by SLA deadline; status="
                                + status
                                + " deadline="
                                + deadline
                                + " now="
                                + now);
            }
            Thread.sleep(200);
        }
    }

    private Process startClaimHold(long actionJobId, Path tokenFile, int leaseSeconds) throws Exception {
        String java = ProcessHandle.current().info().command().orElse("java");
        Path childLog = Files.createTempFile("sla-child-", ".log");
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(MxzClaimAndHoldMain.class.getName());
        cmd.add("action");
        cmd.add(Long.toString(actionJobId));
        cmd.add(tokenFile.toAbsolutePath().toString());
        cmd.add(JDBC);
        cmd.add(DB_USER);
        cmd.add(DB_PASS);
        cmd.add(Integer.toString(leaseSeconds));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(childLog.toFile());
        Process p = pb.start();
        // Stash log path on process for awaitToken diagnostics via system property is awkward;
        // store beside token file name convention.
        Files.writeString(tokenFile.resolveSibling(tokenFile.getFileName() + ".childlog"), childLog.toString());
        return p;
    }

    private String awaitToken(Path tokenFile, Duration timeout, Process child, long actionJobId)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Files.exists(tokenFile) && Files.size(tokenFile) > 0) {
                String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
            if (!child.isAlive()) {
                break;
            }
            Thread.sleep(100);
        }
        Path logPointer = tokenFile.resolveSibling(tokenFile.getFileName() + ".childlog");
        String childOut = "";
        if (Files.exists(logPointer)) {
            Path childLog = Path.of(Files.readString(logPointer).trim());
            if (Files.exists(childLog)) {
                childOut = Files.readString(childLog, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError(
                "child did not write token file: "
                        + tokenFile
                        + " alive="
                        + child.isAlive()
                        + " exit="
                        + (child.isAlive() ? "n/a" : child.exitValue())
                        + " actionStatus="
                        + statusOfAction(actionJobId)
                        + " childLog=\n"
                        + childOut);
    }

    private long createOnceReminder(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(30).withNano(0);
        Map<String, Object> body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "07-s6 takeover wall-clock SLA",
                "scenarioConfig", Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config",
                        Map.of(
                                "type", "ONCE",
                                "localDate", occurrence.toLocalDate().toString(),
                                "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId", "Asia/Shanghai"))));
        JsonNode created = post("/api/v1/task-definitions", body);
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private String statusOfAction(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
