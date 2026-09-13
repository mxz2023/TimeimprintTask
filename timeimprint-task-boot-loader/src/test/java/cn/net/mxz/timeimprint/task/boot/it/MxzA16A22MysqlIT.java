package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.boot.fixture.DenyActionPolicyFixture;
import cn.net.mxz.timeimprint.task.boot.fixture.WebhookActionFixture;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.MxzActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
 * A16（退避 / Policy 退还计数 / EXPIRED）与 A22（OWNER 回退、显式 RECIPIENT、去重、超过 10 人拒绝）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA16A22MysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MxzTaskGateway gateway;

    @Autowired
    MxzSignalProcessingService signalProcessing;

    @Autowired
    MxzActionWorker actionWorker;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        r.add("spring.datasource.username", () -> "tit");
        r.add("spring.datasource.password", () -> "tit_local");
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @BeforeEach
    void resetFixtures() {
        WebhookActionFixture.reset();
        DenyActionPolicyFixture.reset();
    }

    @Test
    void a16RetryBackoffPolicyRefundAndExpire() throws Exception {
        long definitionId = createOnce("A16 " + UUID.randomUUID());
        long signalId = signalOf(definitionId);
        signalProcessing.processSignal(signalId);
        long actionJobId = readyAction(definitionId);
        makeWebhookDue(actionJobId);

        WebhookActionFixture.failNext(1);
        actionWorker.executeAction(actionJobId);
        assertEquals("RETRY_WAIT", statusOf(actionJobId));
        LocalDateTime next = jdbc.queryForObject(
                "SELECT next_attempt_at FROM tt_action_job WHERE action_job_id = ?",
                LocalDateTime.class,
                actionJobId);
        assertNotNull(next);
        Duration delay = Duration.between(LocalDateTime.now(ZoneOffset.UTC), next);
        assertTrue(delay.getSeconds() >= 3 && delay.getSeconds() <= 8, "first backoff ~5s, was " + delay);

        Integer attemptsAfterFail = jdbc.queryForObject(
                "SELECT attempt_count FROM tt_action_job WHERE action_job_id = ?", Integer.class, actionJobId);
        assertEquals(1, attemptsAfterFail);

        // Exhaust attempts: second claim reaches max_attempts=2 → DEAD (cannot lower max below attempt_count).
        jdbc.update(
                "UPDATE tt_action_job SET next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, max_attempts = 2 WHERE action_job_id = ?",
                actionJobId);
        WebhookActionFixture.failNext(1);
        actionWorker.executeAction(actionJobId);
        assertEquals("DEAD", statusOf(actionJobId));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT attempt_count FROM tt_action_job WHERE action_job_id = ?",
                        Integer.class,
                        actionJobId));

        // Fresh action for Policy refund + EXPIRED.
        long def2 = createOnce("A16p " + UUID.randomUUID());
        signalProcessing.processSignal(signalOf(def2));
        long action2 = readyAction(def2);
        makeWebhookDue(action2);
        DenyActionPolicyFixture.DENY.set(true);
        actionWorker.executeAction(action2);
        assertEquals("READY", statusOf(action2));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT attempt_count FROM tt_action_job WHERE action_job_id = ?",
                        Integer.class,
                        action2));
        Integer policyAttempts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_attempt WHERE action_job_id = ? AND outcome = 'POLICY_BLOCKED'",
                Integer.class,
                action2);
        assertEquals(1, policyAttempts);
        DenyActionPolicyFixture.reset();

        jdbc.update(
                "UPDATE tt_action_job SET expires_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                action2);
        actionWorker.executeAction(action2);
        assertEquals("EXPIRED", statusOf(action2));
    }

    @Test
    void a22RecipientOwnerFallbackExclusiveDedupAndCap() throws Exception {
        // OWNER only → notify OWNER
        long defOwner = createOnce("A22 owner " + UUID.randomUUID());
        signalProcessing.processSignal(signalOf(defOwner));
        long actionOwner = readyAction(defOwner);
        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                actionOwner);
        actionWorker.executeAction(actionOwner);
        assertEquals(1, inboxCount(defOwner));
        assertEquals(
                "local-actor",
                jdbc.queryForObject(
                        "SELECT recipient_id FROM tt_inbox WHERE definition_id = ?", String.class, defOwner));

        // Explicit RECIPIENT only (OWNER different via JDBC) → no OWNER inbox
        long defRec = createOnce("A22 rec " + UUID.randomUUID());
        jdbc.update(
                "UPDATE tt_task_participant SET principal_id = 'owner-x' WHERE definition_id = ? AND role_code = 'OWNER'",
                defRec);
        insertRecipient(defRec, "recv-a");
        insertRecipient(defRec, "recv-b");
        signalProcessing.processSignal(signalOf(defRec));
        Integer actions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?", Integer.class, defRec);
        assertEquals(2, actions);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ? AND target_id = 'owner-x'",
                        Integer.class,
                        defRec));

        // >10 RECIPIENT → Signal ignored, no Action
        long defCap = createOnce("A22 cap " + UUID.randomUUID());
        for (int i = 0; i < 11; i++) {
            insertRecipient(defCap, "cap-" + i);
        }
        long sigCap = signalOf(defCap);
        signalProcessing.processSignal(sigCap);
        assertEquals(
                "IGNORED",
                jdbc.queryForObject(
                        "SELECT process_status FROM tt_task_signal WHERE signal_id = ?", String.class, sigCap));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?", Integer.class, defCap));
    }

    private void makeWebhookDue(long actionJobId) {
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
    }

    private void insertRecipient(long definitionId, String principalId) {
        jdbc.update(
                """
                INSERT INTO tt_task_participant(
                  definition_id, instance_id, principal_type, principal_id, role_code, source_code,
                  metadata_json, created_at, updated_at)
                VALUES(?, NULL, 'USER', ?, 'RECIPIENT', 'TEST', CAST('{}' AS JSON), UTC_TIMESTAMP(), UTC_TIMESTAMP())
                """,
                definitionId,
                principalId);
    }

    private long createOnce(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(20).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", title,
                        "description", "a16a22",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
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
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private long signalOf(long definitionId) {
        return jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1", Long.class, definitionId);
    }

    private long readyAction(long definitionId) {
        Long id = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE definition_id = ? AND status = 'READY' LIMIT 1",
                Long.class,
                definitionId);
        assertNotNull(id);
        return id;
    }

    private String statusOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private int inboxCount(long definitionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?", Integer.class, definitionId);
        return n == null ? 0 : n;
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
