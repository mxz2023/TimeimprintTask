package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.runtime.action.recovery.ActionLeaseReaper;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.context.DynamicPropertySource;
import cn.net.mxz.timeimprint.task.boot.bootstrap.LoopbackAddressEnvironmentPostProcessor;

/**
 * A39：local 回环绑定、liveness/readiness 边界、停机拒绝新写并停止领取；租约回收不伪造 READY 批量重置。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A39ShutdownMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RuntimeAdmission admission;

    @Autowired
    ActionWorker actionWorker;

    @Autowired
    ActionLeaseReaper actionLeaseReaper;

    @Autowired
    ApplicationAvailability availability;

    @Autowired
    ApplicationContext applicationContext;

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
        r.add("server.address", () -> "127.0.0.1");
    }

    @AfterEach
    void resetAdmission() {
        admission.resetForTests();
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void a39NonLoopbackAddressRejected() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> LoopbackAddressEnvironmentPostProcessor.assertLoopback("SERVER_ADDRESS", "0.0.0.0"));
        assertTrue(ex.getMessage().contains("loopback"));
        LoopbackAddressEnvironmentPostProcessor.assertLoopback("SERVER_ADDRESS", "127.0.0.1");
    }

    @Test
    void a39LivenessIndependentOfDbGroup() throws Exception {
        JsonNode live = get("/actuator/health/liveness");
        assertEquals("UP", live.path("status").asText(), live.toString());
        JsonNode ready = get("/actuator/health/readiness");
        assertEquals("UP", ready.path("status").asText(), ready.toString());
    }

    @Test
    void a39ShutdownRejectsWritesStopsClaimsLeaseReaperTakeover() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusHours(3).withSecond(0).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A39 " + UUID.randomUUID(),
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
                                "config",
                                Map.of(
                                        "type", "ONCE",
                                        "localDate", occurrence.toLocalDate().toString(),
                                        "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        long revision = created.path("data").path("revision").asLong();

        admission.beginShutdown();
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        assertFalse(admission.acceptingWrites());
        assertFalse(admission.acceptingClaims());
        assertEquals(ReadinessState.REFUSING_TRAFFIC, availability.getReadinessState());

        JsonNode rejected = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", revision,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("RETRY_LATER", rejected.path("code").asText(), rejected.toString());

        JsonNode live = get("/actuator/health/liveness");
        assertEquals("UP", live.path("status").asText(), live.toString());
        JsonNode ready = get("/actuator/health/readiness");
        assertTrue(
                "DOWN".equals(ready.path("status").asText())
                        || "OUT_OF_SERVICE".equals(ready.path("status").asText()),
                "readiness must refuse traffic; was " + ready);

        String decoyKey = "A39-DECOY:" + UUID.randomUUID();
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        Long controlGen = jdbc.queryForObject(
                "SELECT control_generation FROM tt_task_definition WHERE definition_id = ?",
                Long.class,
                definitionId);
        Long transitionId = jdbc.queryForObject(
                "SELECT transition_id FROM tt_task_transition WHERE instance_id = ? ORDER BY transition_id LIMIT 1",
                Long.class,
                instanceId);
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
                    UTC_TIMESTAMP() - INTERVAL 1 SECOND, 'READY', 0, 3, UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                    UTC_TIMESTAMP(), UTC_TIMESTAMP()
                )
                """,
                definitionId,
                instanceId,
                transitionId,
                controlGen,
                decoyKey);
        Long decoyId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE action_key = ?", Long.class, decoyKey);

        actionWorker.pollAndExecute();
        assertEquals(
                "READY",
                jdbc.queryForObject("SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, decoyId));

        String token = UUID.randomUUID().toString();
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  status = 'RUNNING',
                  lease_owner = 'dying-owner',
                  lease_until = UTC_TIMESTAMP() - INTERVAL 10 SECOND,
                  execution_token = ?,
                  updated_at = UTC_TIMESTAMP()
                WHERE action_job_id = ?
                """,
                token,
                decoyId);
        actionLeaseReaper.pollAndRecover();
        assertEquals(
                "RETRY_WAIT",
                jdbc.queryForObject("SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, decoyId));

        // Shutdown itself must not leave the job RUNNING; reclaim yields RETRY_WAIT for another owner.
        // Old dying token cannot CAS-complete.
        Integer cas = jdbc.update(
                """
                UPDATE tt_action_job SET status = 'SUCCEEDED', outcome_code = 'STALE'
                WHERE action_job_id = ? AND status = 'RUNNING' AND execution_token = ?
                """,
                decoyId,
                token);
        assertEquals(0, cas);
        assertEquals(
                "RETRY_WAIT",
                jdbc.queryForObject("SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, decoyId));
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
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
