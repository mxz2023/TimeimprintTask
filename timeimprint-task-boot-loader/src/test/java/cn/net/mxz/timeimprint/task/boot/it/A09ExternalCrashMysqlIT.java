package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.boot.fixture.WebhookActionFixture;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.runtime.ActionLeaseReaper;
import cn.net.mxz.timeimprint.task.service.runtime.ActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A09：EXTERNAL 调用前/中/后崩溃 — 未开始可重试；effectStartedAt 已提交则 UNKNOWN 且不自动重发。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A09ExternalCrashMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

    @Autowired
    ActionWorker actionWorker;

    @Autowired
    ActionLeaseReaper leaseReaper;

    @Autowired
    ActionJobExecutionPort actionPort;

    @Autowired
    TransactionBoundary tx;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @BeforeEach
    void resetFixture() {
        WebhookActionFixture.reset();
    }

    @Test
    void a09CrashBeforeEffectAllowsRetry() throws Exception {
        long actionJobId = prepareExternalAction("A09 before-effect");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = tx.execute(() -> actionPort
                .claimForExecution(actionJobId, "crash-test", now.plusSeconds(30), now)
                .orElseThrow());
        assertEquals("RUNNING", statusOf(actionJobId));
        assertTrue(effectStartedAt(actionJobId) == null);

        expireLease(actionJobId);
        assertEquals(1, leaseReaper.recoverBatch());

        assertEquals("RETRY_WAIT", statusOf(actionJobId));
        assertEquals("RETRYABLE_FAILURE", latestAttemptOutcome(actionJobId));
        assertEquals(0, WebhookActionFixture.INVOKE_COUNT.get());

        // Make retry due and re-execute — must call handler once.
        jdbc.update(
                "UPDATE tt_action_job SET next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                        + "available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                actionJobId);
        actionWorker.executeAction(actionJobId);
        assertEquals(1, WebhookActionFixture.INVOKE_COUNT.get());
        assertEquals("SUCCEEDED", statusOf(actionJobId));

        // Old token must not overwrite.
        actionPort.completeWithToken(
                actionJobId, token, "UNKNOWN", "STALE", "should not win", Instant.now().truncatedTo(ChronoUnit.SECONDS));
        assertEquals("SUCCEEDED", statusOf(actionJobId));
    }

    @Test
    void a09CrashAfterEffectGoesUnknownNoAutoRetry() throws Exception {
        long actionJobId = prepareExternalAction("A09 after-effect");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        tx.execute(() -> {
            String token = actionPort
                    .claimForExecution(actionJobId, "crash-test", now.plusSeconds(30), now)
                    .orElseThrow();
            assertTrue(actionPort.markEffectStarted(actionJobId, token, now));
            return null;
        });
        assertNotNull(effectStartedAt(actionJobId));
        assertEquals("RUNNING", statusOf(actionJobId));

        expireLease(actionJobId);
        assertEquals(1, leaseReaper.recoverBatch());

        assertEquals("UNKNOWN", statusOf(actionJobId));
        assertEquals("LEASE_EXPIRED", outcomeOf(actionJobId));
        assertEquals("UNKNOWN", latestAttemptOutcome(actionJobId));
        assertEquals(0, WebhookActionFixture.INVOKE_COUNT.get());

        // Not re-queued: execute / recover must not call handler or flip status.
        actionWorker.executeAction(actionJobId);
        assertEquals(0, leaseReaper.recoverBatch());
        assertEquals(0, WebhookActionFixture.INVOKE_COUNT.get());
        assertEquals("UNKNOWN", statusOf(actionJobId));
    }

    @Test
    void a09CrashAfterResultKeepsSucceeded() throws Exception {
        long actionJobId = prepareExternalAction("A09 after-result");
        actionWorker.executeAction(actionJobId);
        assertEquals(1, WebhookActionFixture.INVOKE_COUNT.get());
        assertEquals("SUCCEEDED", statusOf(actionJobId));
        assertNotNull(effectStartedAt(actionJobId));

        // Completed rows are not RUNNING — reaper must ignore them.
        assertTrue(actionPort.listExpiredRunningIds(50).stream().noneMatch(id -> id == actionJobId));
        assertEquals(0, leaseReaper.recoverBatch());
        assertEquals("SUCCEEDED", statusOf(actionJobId));
        assertEquals(1, WebhookActionFixture.INVOKE_COUNT.get());
    }

    private long prepareExternalAction(String title) throws Exception {
        long definitionId = createS02(title);
        Long instanceId = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND status = 'READY'
                ORDER BY available_at DESC LIMIT 1
                """,
                Long.class,
                instanceId);
        assertNotNull(actionJobId);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  handler_key = ?,
                  execution_mode = 'EXTERNAL',
                  available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND
                WHERE action_job_id = ?
                """,
                WebhookActionFixture.HANDLER_KEY,
                actionJobId);
        return actionJobId;
    }

    private void expireLease(long actionJobId) {
        jdbc.update(
                "UPDATE tt_action_job SET lease_until = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                actionJobId);
    }

    private long createS02(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                "zoneId", "Asia/Shanghai");
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", title,
                        "description", "",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings",
                        List.of(Map.of(
                                "bindingKey", "primary",
                                "providerKey", "calendar",
                                "schemaVersion", 1,
                                "config", daily))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private String statusOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private String outcomeOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT outcome_code FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private String latestAttemptOutcome(long actionJobId) {
        return jdbc.queryForObject(
                """
                SELECT outcome FROM tt_action_attempt
                WHERE action_job_id = ? ORDER BY attempt_no DESC LIMIT 1
                """,
                String.class,
                actionJobId);
    }

    private Object effectStartedAt(long actionJobId) {
        return jdbc.query(
                """
                SELECT effect_started_at FROM tt_action_attempt
                WHERE action_job_id = ? ORDER BY attempt_no DESC LIMIT 1
                """,
                rs -> rs.next() ? rs.getObject(1) : null,
                actionJobId);
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
