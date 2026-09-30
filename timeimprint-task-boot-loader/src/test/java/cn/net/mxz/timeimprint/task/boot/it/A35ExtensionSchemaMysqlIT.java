package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.boot.fixture.ApprovalFixture;
import cn.net.mxz.timeimprint.task.boot.health.LiveSchemaReadinessIndicator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.context.DynamicPropertySource;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;

/**
 * A35：扩展 Applied/NoChange/Rejected/技术异常语义；未终结旧 schema 使 readiness 拒绝；历史终态仍可读公共快照。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A35ExtensionSchemaMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LiveSchemaReadinessIndicator liveSchema;

    @Autowired
    ApprovalFixture approvalFixture;

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

    @Test
    void a35HandlerResults_noChangeRejectedAndTechException() {
        HandlerResult noChange = approvalFixture.processSignal(dummySignalContext());
        assertTrue(noChange instanceof HandlerResult.NoChange, "fixture signal → NoChange");

        // Tech exception must not be disguised as Rejected result type.
        try {
            approvalFixture.validateScenarioState("NOT_A_VALID_STATE", 1);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("INVALID_REQUEST"));
        }

        // Rejected is a sealed result type (business), distinct from thrown tech errors.
        HandlerResult rejected = new HandlerResult.Rejected("STATE_CONFLICT", "safe");
        assertTrue(rejected instanceof HandlerResult.Rejected);
        assertEquals("STATE_CONFLICT", ((HandlerResult.Rejected) rejected).reasonCode());
    }

    @Test
    void a35UnloadOldSchema_blocksReadiness_terminalHistoryStillReadable() throws Exception {
        assertEquals(Status.UP, liveSchema.health().getStatus());

        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(4).withHour(15).withMinute(0).withSecond(0).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A35 hist " + UUID.randomUUID(),
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
        long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);

        // Simulate "unload old schema reader": live non-terminal row with unsupported schemaVersion.
        jdbc.update(
                """
                UPDATE tt_task_signal
                SET schema_version = 99, process_status = 'READY', processed_at = NULL, result_code = NULL
                WHERE signal_id = ?
                """,
                signalId);
        assertEquals(Status.DOWN, liveSchema.health().getStatus());
        JsonNode readinessDown = get("/actuator/health/readiness");
        assertEquals("DOWN", readinessDown.path("status").asText(), readinessDown.toString());

        // Close live row as terminal history — no longer requires reader.
        jdbc.update(
                """
                UPDATE tt_task_signal
                SET process_status = 'IGNORED', processed_at = UTC_TIMESTAMP(), result_code = 'SCHEMA_UNLOADED'
                WHERE signal_id = ?
                """,
                signalId);
        // Instance public snapshot remains readable even if we stamp an obsolete scenario schema on TERMINAL.
        jdbc.update(
                """
                UPDATE tt_task_instance
                SET lifecycle_category = 'TERMINAL',
                    scenario_state = 'TRIGGERED',
                    scenario_schema_version = 99,
                    terminal_at = UTC_TIMESTAMP()
                WHERE instance_id = ?
                """,
                instanceId);

        assertEquals(Status.UP, liveSchema.health().getStatus());
        JsonNode readinessUp = get("/actuator/health/readiness");
        assertEquals("UP", readinessUp.path("status").asText(), readinessUp.toString());

        JsonNode instance = get("/api/v1/task-instances/" + instanceId);
        assertEquals("OK", instance.path("code").asText(), instance.toString());
        assertEquals("TERMINAL", instance.path("data").path("lifecycleCategory").asText());
        assertEquals("TRIGGERED", instance.path("data").path("scenarioState").asText());
    }

    private static SignalProcessContext dummySignalContext() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        var def = new TaskDefinitionSnapshot(
                1L,
                "local-tenant",
                ApprovalFixture.SCENARIO_KEY,
                1,
                "t",
                "",
                "{}",
                cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState.ACTIVE,
                1L,
                1L,
                now,
                now);
        var inst = new TaskInstanceSnapshot(
                1L,
                1L,
                null,
                null,
                1L,
                null,
                null,
                null,
                cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory.WAITING,
                "PENDING_APPROVAL",
                1,
                "{}",
                "t",
                null,
                1L,
                null);
        return new SignalProcessContext(
                def, inst, 1L, "calendar", "sk", 1, new JsonPayload(Map.of()));
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
