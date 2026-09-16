package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 07 §6 独立公平性：≥10 定义、每定义 >100 条积压；单轮领取 ≤ CLAIM_BATCH_SIZE(100)；
 * 新到期 S01 在 10 秒内产生收件，不被积压饿死。
 *
 * <p>关闭 {@code @Scheduled}，用显式 {@link ActionWorker#pollOnceForTests()} 驱动，避免与其它
 * Spring 测试上下文的后台 Worker 争抢同一数据库。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("dual-process-it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FairnessBacklogDualProcessIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int BACKLOG_DEFS = 10;
    private static final int BACKLOG_PER_DEF = 101;
    private static final int CLAIM_BATCH = 100;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignalProcessingService signalProcessing;

    @Autowired
    ActionWorker actionWorker;

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
        r.add("INSTANCE_ID", () -> "fair-parent-" + UUID.randomUUID());
        r.add("server.address", () -> "127.0.0.1");
        r.add("CLAIM_BATCH_SIZE", () -> Integer.toString(CLAIM_BATCH));
        r.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Test
    void backlogDoesNotStarveFreshS01WithinTenSeconds() throws Exception {
        assertEquals(CLAIM_BATCH, actionWorker.claimBatchSize());
        String runPrefix = "fair-" + UUID.randomUUID().toString().substring(0, 8);

        for (int d = 0; d < BACKLOG_DEFS; d++) {
            seedBacklogDefinition(runPrefix, d);
            Integer perDef = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM tt_action_job
                    WHERE status = 'READY' AND action_key LIKE ?
                    """,
                    Integer.class,
                    runPrefix + "-backlog-" + d + "-%");
            assertTrue(
                    perDef != null && perDef > 100,
                    "def " + d + " backlog READY count=" + perDef + " (need >100)");
        }

        int polled = actionWorker.pollOnceForTests();
        assertTrue(polled <= CLAIM_BATCH, "single round must respect CLAIM_BATCH_SIZE; got " + polled);
        assertEquals(CLAIM_BATCH, polled, "with >100 due jobs, one round should take full batch");

        long freshDef = createOnceReminder(
                runPrefix + " fresh", ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(30));
        Long freshInstance = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, freshDef);
        Long freshSignal = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, freshDef);
        signalProcessing.processSignal(freshSignal);
        Long freshAction = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status = 'READY' LIMIT 1
                """,
                Long.class,
                freshDef);
        assertTrue(freshAction != null);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP(),
                  next_attempt_at = UTC_TIMESTAMP()
                WHERE action_job_id = ?
                """,
                freshAction);

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            actionWorker.pollOnceForTests();
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?", Integer.class, freshInstance);
            if (n != null && n >= 1) {
                break;
            }
            Thread.sleep(50);
        }

        Integer inbox = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?", Integer.class, freshInstance);
        assertEquals(1, inbox, "fresh S01 inbox not produced within 10s under backlog pressure");
    }

    private void seedBacklogDefinition(String runPrefix, int index) throws Exception {
        long definitionId = createOnceReminder(
                runPrefix + " backlog-" + index,
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(2));
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        signalProcessing.processSignal(signalId);
        Long templateAction = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status = 'READY' LIMIT 1
                """,
                Long.class,
                definitionId);
        assertTrue(templateAction != null);
        for (int i = 0; i < BACKLOG_PER_DEF; i++) {
            String actionKey = runPrefix + "-backlog-" + index + "-" + i;
            int inserted = jdbc.update(
                    """
                    INSERT INTO tt_action_job (
                      tenant_id, definition_id, instance_id, transition_id,
                      definition_control_generation, parent_action_job_id, redrive_no,
                      handler_key, action_key, execution_mode, schema_version,
                      target_type, target_id, payload_json, payload_hash,
                      available_at, expires_at, status, attempt_count, max_attempts, next_attempt_at,
                      created_at, updated_at)
                    SELECT
                      tenant_id, definition_id, instance_id, transition_id,
                      definition_control_generation, NULL, 0,
                      handler_key, ?, execution_mode, schema_version,
                      target_type, target_id, payload_json, payload_hash,
                      UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                      UTC_TIMESTAMP() + INTERVAL 1 DAY,
                      'READY', 0, max_attempts,
                      UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                      UTC_TIMESTAMP(), UTC_TIMESTAMP()
                    FROM tt_action_job WHERE action_job_id = ?
                    """,
                    actionKey,
                    templateAction);
            assertEquals(1, inserted, "clone insert failed for " + actionKey);
        }
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  status = 'CANCELLED', outcome_code = 'TEST_BACKLOG',
                  completed_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP(),
                  lease_owner = NULL, lease_until = NULL, execution_token = NULL
                WHERE action_job_id = ?
                """,
                templateAction);
    }

    private long createOnceReminder(String title, ZonedDateTime occurrence) throws Exception {
        Map<String, Object> body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "07-s6 fairness backlog",
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
                                "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                                "zoneId", "Asia/Shanghai"))));
        JsonNode created = post("/api/v1/task-definitions", body);
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
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
