package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import cn.net.mxz.timeimprint.task.boot.it.support.ClaimAndHoldMain;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.action.recovery.ActionLeaseReaper;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import cn.net.mxz.timeimprint.task.service.runtime.signal.recovery.SignalLeaseReaper;

/**
 * A04 / A20：真二 JVM — 子进程领取后被杀，父进程租约回收接管；旧 token CAS 为 0；最终一份事实。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("dual-process-it")
class A04A20DualProcessIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String JDBC =
            "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC";
    private static final String DB_USER = "tit";
    private static final String DB_PASS = "tit_local";

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

    @Autowired
    SignalProcessingService signalProcessing;

    @Autowired
    SignalLeaseReaper signalLeaseReaper;

    @Autowired
    TaskSignalRepository signalRepository;

    @Autowired
    ActionWorker actionWorker;

    @Autowired
    ActionLeaseReaper actionLeaseReaper;

    @Autowired
    ActionJobExecutionPort actionPort;

    @Autowired
    TransactionBoundary tx;

    private final HttpClient http = new ItHttpFixture();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> JDBC);
        r.add("spring.datasource.username", () -> DB_USER);
        r.add("spring.datasource.password", () -> DB_PASS);
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
        r.add("INSTANCE_ID", () -> "dual-parent-" + UUID.randomUUID());
        r.add("server.address", () -> "127.0.0.1");
    }

    @Test
    void a04SignalClaimCrashThenTakeoverRejectsOldToken() throws Exception {
        long definitionId = createOnceReminder("A04 dual " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ?",
                Long.class,
                definitionId,
                instanceId);
        assertNotNull(signalId);

        Path tokenFile = Files.createTempFile("a04-token-", ".txt");
        Process child = startClaimHold("signal", signalId, tokenFile);
        try {
            String oldToken = awaitToken(tokenFile, Duration.ofSeconds(20));
            assertEquals("RUNNING", statusOfSignal(signalId));

            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));

            jdbc.update(
                    "UPDATE tt_task_signal SET lease_until = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE signal_id = ?",
                    signalId);
            assertTrue(signalLeaseReaper.recoverBatch() >= 1);
            assertEquals("RETRY_WAIT", statusOfSignal(signalId));

            // Make signal due immediately after recover backoff.
            jdbc.update(
                    "UPDATE tt_task_signal SET next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE signal_id = ?",
                    signalId);

            signalProcessing.processSignal(signalId);
            assertEquals("SUCCEEDED", statusOfSignal(signalId));

            Integer transitions = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tt_task_transition WHERE instance_id = ? AND source_type = 'SIGNAL'",
                    Integer.class,
                    instanceId);
            assertEquals(1, transitions, "only one Signal migration chain");

            Boolean oldCas = tx.execute(
                    () -> signalRepository.completeWithToken(
                            signalId, oldToken, "SUCCEEDED", "STALE", "should fail", Instant.now()));
            assertEquals(Boolean.FALSE, oldCas, "old token CAS must be 0");
        } finally {
            child.destroyForcibly();
            Files.deleteIfExists(tokenFile);
        }
    }

    @Test
    void a20ActionClaimCrashThenTakeoverSingleInbox() throws Exception {
        long definitionId = createOnceReminder("A20 dual " + UUID.randomUUID());
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
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                actionJobId);

        Path tokenFile = Files.createTempFile("a20-token-", ".txt");
        Process child = startClaimHold("action", actionJobId, tokenFile);
        try {
            String oldToken = awaitToken(tokenFile, Duration.ofSeconds(20));
            assertEquals("RUNNING", statusOfAction(actionJobId));

            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));

            jdbc.update(
                    "UPDATE tt_action_job SET lease_until = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                    actionJobId);
            assertTrue(actionLeaseReaper.recoverBatch() >= 1);
            assertEquals("RETRY_WAIT", statusOfAction(actionJobId));

            jdbc.update(
                    "UPDATE tt_action_job SET next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                            + "available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                    actionJobId);
            actionWorker.executeAction(actionJobId);
            assertEquals("SUCCEEDED", statusOfAction(actionJobId));

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

    private Process startClaimHold(String mode, long id, Path tokenFile) throws Exception {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(ClaimAndHoldMain.class.getName());
        cmd.add(mode);
        cmd.add(Long.toString(id));
        cmd.add(tokenFile.toAbsolutePath().toString());
        cmd.add(JDBC);
        cmd.add(DB_USER);
        cmd.add(DB_PASS);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }

    private static String awaitToken(Path tokenFile, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Files.exists(tokenFile) && Files.size(tokenFile) > 0) {
                String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
                if (!token.isEmpty()) {
                    return token;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("child did not write token file: " + tokenFile);
    }

    private long createOnceReminder(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(30).withNano(0);
        Map<String, Object> body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "dual-process crash takeover",
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

    private String statusOfSignal(long signalId) {
        return jdbc.queryForObject(
                "SELECT process_status FROM tt_task_signal WHERE signal_id = ?", String.class, signalId);
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
