package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;
import cn.net.mxz.timeimprint.task.boot.fixture.WebhookActionFixture;

/**
 * A08：pause 与 EXTERNAL Action — pause 先则无调用；effectStartedAt 先则调用可继续并记录结果。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A08ExternalPauseMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

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

    @BeforeEach
    void resetFixture() {
        WebhookActionFixture.reset();
    }

    @Test
    void a08PauseBeforeExternalSkipsCall() throws Exception {
        long actionJobId = prepareExternalAction("A08 pause-first");
        pause(definitionIdOf(actionJobId));

        actionWorker.executeAction(actionJobId);

        assertEquals(0, WebhookActionFixture.INVOKE_COUNT.get(), "pause-first must not call EXTERNAL handler");
        assertEquals("CANCELLED", statusOf(actionJobId));
        assertEquals("CONTROL_BARRIER", outcomeOf(actionJobId));
        assertNull(effectStartedAt(actionJobId), "no effectStartedAt when pause wins");
    }

    @Test
    void a08EffectStartedBeforePauseCompletesCall() throws Exception {
        long actionJobId = prepareExternalAction("A08 effect-first");
        WebhookActionFixture.armHold();

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                actionWorker.executeAction(actionJobId);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        }, "a08-external-worker");
        worker.start();

        assertTrue(WebhookActionFixture.awaitEntered(10_000), "handler must enter after effectStartedAt");
        assertNotNull(effectStartedAt(actionJobId), "effectStartedAt must commit before EXTERNAL call body");
        assertEquals("RUNNING", statusOf(actionJobId));

        pause(definitionIdOf(actionJobId));
        WebhookActionFixture.releaseHold();
        assertTrue(done.await(15, TimeUnit.SECONDS), "worker must finish");
        assertNull(error.get(), () -> String.valueOf(error.get()));

        assertEquals(1, WebhookActionFixture.INVOKE_COUNT.get());
        assertEquals("SUCCEEDED", statusOf(actionJobId));
        assertEquals("FIXTURE_OK", outcomeOf(actionJobId));
        assertNotNull(effectStartedAt(actionJobId));
        String attemptOutcome = jdbc.queryForObject(
                """
                SELECT outcome FROM tt_action_attempt
                WHERE action_job_id = ? ORDER BY attempt_no DESC LIMIT 1
                """,
                String.class,
                actionJobId);
        assertEquals("SUCCEEDED", attemptOutcome);
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

    private void pause(long definitionId) throws Exception {
        JsonNode def = get("/api/v1/task-definitions/" + definitionId);
        JsonNode paused = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", def.path("data").path("revision").asLong(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", paused.path("code").asText(), paused.toString());
    }

    private long definitionIdOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT definition_id FROM tt_action_job WHERE action_job_id = ?", Long.class, actionJobId);
    }

    private String statusOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private String outcomeOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT outcome_code FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
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

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
