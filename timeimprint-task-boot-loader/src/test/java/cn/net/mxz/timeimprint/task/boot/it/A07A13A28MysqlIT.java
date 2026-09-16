package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A07（pause↔Signal 屏障）、A13（幂等冲突与同 revision 并发）、A28（resume 后旧 Worker 代次隔离）。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A07A13A28MysqlIT {

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
    void a07PauseBeforeSignalIgnores_SignalAfterPauseKeepsFacts() throws Exception {
        // Case 1: pause first → Signal must not migrate WAITING.
        long defPauseFirst = createS02("A07 pause-first");
        Long waitingId = firstInstanceId(defPauseFirst);
        Long signalId = signalForInstance(waitingId);
        assertEquals("READY", signalStatus(signalId));

        pause(defPauseFirst);
        gateway.processSignal(signalId);

        assertEquals("IGNORED", signalStatus(signalId));
        JsonNode waiting = get("/api/v1/task-instances/" + waitingId);
        assertEquals("CANCELLED", waiting.path("data").path("scenarioState").asText(), waiting.toString());
        assertEquals("TERMINAL", waiting.path("data").path("lifecycleCategory").asText());
        Integer actions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?", Integer.class, waitingId);
        assertEquals(0, actions == null ? -1 : actions);

        // Case 2: Signal first → Applied facts retained after pause.
        long defSignalFirst = createS02("A07 signal-first");
        Long pendingId = firstInstanceId(defSignalFirst);
        Long appliedSignal = signalForInstance(pendingId);
        gateway.processSignal(appliedSignal);
        assertEquals("SUCCEEDED", signalStatus(appliedSignal));
        JsonNode pendingBefore = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingBefore.path("data").path("scenarioState").asText());
        Integer actionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                pendingId);
        assertTrue(actionsBefore != null && actionsBefore >= 1, "signal-first must leave action facts");
        Integer transitionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_transition WHERE instance_id = ?",
                Integer.class,
                pendingId);
        assertTrue(transitionsBefore != null && transitionsBefore >= 1);

        pause(defSignalFirst);
        JsonNode pendingAfter = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingAfter.path("data").path("scenarioState").asText());
        Integer actionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                pendingId);
        assertEquals(actionsBefore, actionsAfter);
        Integer transitionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_transition WHERE instance_id = ?",
                Integer.class,
                pendingId);
        assertEquals(transitionsBefore, transitionsAfter);
        assertEquals("SUCCEEDED", signalStatus(appliedSignal));
    }

    @Test
    void a13IdempotencyConflictAndConcurrentSameRevision() throws Exception {
        long definitionId = createS02("A13 idempotency");
        Long instanceId = firstInstanceId(definitionId);
        gateway.processSignal(signalForInstance(instanceId));
        JsonNode pending = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", pending.path("data").path("scenarioState").asText());
        long rev = pending.path("data").path("revision").asLong();

        String requestId = UUID.randomUUID().toString();
        Instant until1 = Instant.now().plusSeconds(1800).truncatedTo(ChronoUnit.SECONDS);
        JsonNode first = post(
                "/api/v1/task-instances/" + instanceId + "/commands/snooze",
                Map.of(
                        "requestId", requestId,
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("snoozeUntil", until1.toString())));
        assertEquals("OK", first.path("code").asText(), first.toString());

        Instant until2 = until1.plusSeconds(600);
        JsonNode conflict = post(
                "/api/v1/task-instances/" + instanceId + "/commands/snooze",
                Map.of(
                        "requestId", requestId,
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("snoozeUntil", until2.toString())));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.path("code").asText(), conflict.toString());

        // Same revision, different requestIds: only one migration succeeds.
        long raceDef = createS02("A13 race");
        Long raceId = firstInstanceId(raceDef);
        gateway.processSignal(signalForInstance(raceId));
        long raceRev = get("/api/v1/task-instances/" + raceId).path("data").path("revision").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> c1 = () -> post(
                    "/api/v1/task-instances/" + raceId + "/commands/complete",
                    Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "expectedRevision", raceRev,
                            "commandSchemaVersion", 1,
                            "payload", Map.of()));
            Callable<JsonNode> c2 = () -> post(
                    "/api/v1/task-instances/" + raceId + "/commands/complete",
                    Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "expectedRevision", raceRev,
                            "commandSchemaVersion", 1,
                            "payload", Map.of()));
            Future<JsonNode> f1 = pool.submit(c1);
            Future<JsonNode> f2 = pool.submit(c2);
            JsonNode r1 = f1.get();
            JsonNode r2 = f2.get();
            long okChanged = 0;
            for (JsonNode r : List.of(r1, r2)) {
                if ("OK".equals(r.path("code").asText()) && r.path("data").path("changed").asBoolean()) {
                    okChanged++;
                }
            }
            assertEquals(1, okChanged, "exactly one complete changes; got " + r1 + " / " + r2);
            assertEquals(
                    "COMPLETED",
                    get("/api/v1/task-instances/" + raceId).path("data").path("scenarioState").asText());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a28ResumeIsolatesOldWorkerActions() throws Exception {
        long definitionId = createS02("A28 generation");
        Long pendingId = firstInstanceId(definitionId);
        gateway.processSignal(signalForInstance(pendingId));
        assertEquals(
                "PENDING",
                get("/api/v1/task-instances/" + pendingId).path("data").path("scenarioState").asText());

        Long oldReadyAction = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND status = 'READY'
                ORDER BY available_at DESC LIMIT 1
                """,
                Long.class,
                pendingId);
        assertTrue(oldReadyAction != null && oldReadyAction > 0);
        Long oldGen = jdbc.queryForObject(
                "SELECT definition_control_generation FROM tt_action_job WHERE action_job_id = ?",
                Long.class,
                oldReadyAction);
        assertEquals(1L, oldGen);

        // Materialize one INITIAL inbox fact before control-generation bump.
        Long initialAction = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND status = 'READY'
                ORDER BY available_at ASC LIMIT 1
                """,
                Long.class,
                pendingId);
        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                initialAction);
        actionWorker.executeAction(initialAction);
        assertEquals(
                "SUCCEEDED",
                jdbc.queryForObject(
                        "SELECT status FROM tt_action_job WHERE action_job_id = ?",
                        String.class,
                        initialAction));
        Integer inboxBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?", Integer.class, pendingId);
        assertTrue(inboxBefore != null && inboxBefore >= 1);

        // Do not advance chase available_at: background poller must not race before pause.
        // Explicit executeAction below still re-checks the control barrier.

        pause(definitionId);
        long genAfterPause =
                get("/api/v1/task-definitions/" + definitionId).path("data").path("controlGeneration").asLong();
        assertEquals(2L, genAfterPause);

        JsonNode defPaused = get("/api/v1/task-definitions/" + definitionId);
        JsonNode resumed = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/resume",
                commandBody(defPaused.path("data").path("revision").asLong()));
        assertEquals("OK", resumed.path("code").asText(), resumed.toString());
        long genAfterResume =
                get("/api/v1/task-definitions/" + definitionId).path("data").path("controlGeneration").asLong();
        assertEquals(3L, genAfterResume);

        // Old Worker path: barrier cancels stale READY action.
        actionWorker.executeAction(oldReadyAction);
        String status = jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, oldReadyAction);
        assertEquals("CANCELLED", status);
        String outcome = jdbc.queryForObject(
                "SELECT outcome_code FROM tt_action_job WHERE action_job_id = ?", String.class, oldReadyAction);
        assertEquals("CONTROL_BARRIER", outcome);

        Long newWaitingGen = jdbc.queryForObject(
                """
                SELECT MAX(definition_control_generation) FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING'
                """,
                Long.class,
                definitionId);
        assertEquals(3L, newWaitingGen);
        assertNotEquals(oldGen, newWaitingGen);

        // Pre-pause PENDING + already-succeeded inbox facts retained.
        assertEquals(
                "PENDING",
                get("/api/v1/task-instances/" + pendingId).path("data").path("scenarioState").asText());
        assertEquals(
                "SUCCEEDED",
                jdbc.queryForObject(
                        "SELECT status FROM tt_action_job WHERE action_job_id = ?",
                        String.class,
                        initialAction));
        Integer inboxAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?", Integer.class, pendingId);
        assertEquals(inboxBefore, inboxAfter);
    }

    private long createS02(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                "zoneId", "Asia/Shanghai");
        JsonNode created = post("/api/v1/task-definitions", createBody(title, daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private void pause(long definitionId) throws Exception {
        JsonNode def = get("/api/v1/task-definitions/" + definitionId);
        JsonNode paused = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                commandBody(def.path("data").path("revision").asLong()));
        assertEquals("OK", paused.path("code").asText(), paused.toString());
    }

    private Long firstInstanceId(long definitionId) {
        return jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ?
                ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
    }

    private Long signalForInstance(long instanceId) {
        return jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
    }

    private String signalStatus(long signalId) {
        return jdbc.queryForObject(
                "SELECT process_status FROM tt_task_signal WHERE signal_id = ?", String.class, signalId);
    }

    private Map<String, Object> createBody(String title, Map<String, Object> config) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "",
                "scenarioConfig", Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
    }

    private Map<String, Object> commandBody(long expectedRevision) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", expectedRevision,
                "commandSchemaVersion", 1,
                "payload", Map.of());
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
