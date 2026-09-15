package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.ActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A26：Signal 终态迁移后取消同实例 READY/RETRY_WAIT（保留当前 transition），decoy Action 必须 CANCELLED。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A26S01TerminalMysqlIT {

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

    @Test
    void a26TerminalSignalCancelsDecoyReadyExceptCurrentTransition() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(2).withNano(0);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A26 decoy",
                        "description", "terminal cancels stale READY",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ?",
                Long.class,
                definitionId,
                instanceId);

        JsonNode before = get("/api/v1/task-instances/" + instanceId);
        assertEquals("WAITING", before.path("data").path("lifecycleCategory").asText());

        Long bootstrapTransitionId = jdbc.queryForObject(
                """
                SELECT transition_id FROM tt_task_transition
                WHERE instance_id = ? AND to_revision = 1
                LIMIT 1
                """,
                Long.class,
                instanceId);
        assertNotNull(bootstrapTransitionId);
        Long controlGen = jdbc.queryForObject(
                "SELECT control_generation FROM tt_task_definition WHERE definition_id = ?",
                Long.class,
                definitionId);
        String decoyKey = "DECOY:" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO tt_action_job (
                    tenant_id, definition_id, instance_id, transition_id,
                    definition_control_generation, redrive_no,
                    handler_key, action_key, execution_mode, schema_version,
                    target_type, target_id, payload_json, payload_hash,
                    available_at, status, attempt_count, max_attempts, next_attempt_at,
                    created_at, updated_at
                ) VALUES (
                    'local-tenant', ?, ?, ?,
                    ?, 0,
                    'in_app_notification', ?, 'LOCAL_TRANSACTIONAL', 1,
                    'USER', 'local-actor', CAST('{}' AS JSON), UNHEX(REPEAT('00', 32)),
                    UTC_TIMESTAMP(), 'READY', 0, 3, UTC_TIMESTAMP(),
                    UTC_TIMESTAMP(), UTC_TIMESTAMP()
                )
                """,
                definitionId,
                instanceId,
                bootstrapTransitionId,
                controlGen,
                decoyKey);
        Long decoyId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE action_key = ?",
                Long.class,
                decoyKey);

        gateway.processSignal(signalId);

        JsonNode afterSignal = get("/api/v1/task-instances/" + instanceId);
        assertEquals("TRIGGERED", afterSignal.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", afterSignal.path("data").path("lifecycleCategory").asText());

        String decoyStatus = jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?",
                String.class,
                decoyId);
        assertEquals("CANCELLED", decoyStatus, "decoy on old transition must be cancelled at terminal signal");

        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND
                WHERE instance_id = ? AND action_key LIKE 'INITIAL:%'
                """,
                instanceId);
        Long initialActionId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND action_key LIKE 'INITIAL:%' AND status = 'READY'
                ORDER BY action_job_id ASC LIMIT 1
                """,
                Long.class,
                instanceId);
        assertNotNull(initialActionId);
        actionWorker.executeAction(initialActionId);

        Integer succeeded = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_action_job
                WHERE instance_id = ? AND status = 'SUCCEEDED'
                """,
                Integer.class,
                instanceId);
        assertEquals(1, succeeded);
        Integer inbox = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?",
                Integer.class,
                instanceId);
        assertEquals(1, inbox);
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
