package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A05（complete/skip/snooze 重放与冲突）与 A36（S01/S02 状态组合 + 暂停系统取消）证据。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA05A36StateConcurrencyMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MxzTaskGateway gateway;

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
    void a05CompleteReplayConflictAndRevision() throws Exception {
        PendingInstance pending = createPendingS02("A05 complete");
        long rev = pending.revision();

        String req1 = UUID.randomUUID().toString();
        JsonNode first = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/complete",
                Map.of(
                        "requestId", req1,
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", first.path("code").asText(), first.toString());
        assertTrue(first.path("data").path("changed").asBoolean());
        long revAfter = first.path("data").path("resourceRevision").asLong();

        // same requestId replay → NoChange envelope, revision unchanged
        JsonNode replay = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/complete",
                Map.of(
                        "requestId", req1,
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertFalse(replay.path("data").path("changed").asBoolean());
        assertEquals(revAfter, replay.path("data").path("resourceRevision").asLong());

        // different requestId same terminal → NoChange, no revision bump
        JsonNode again = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/complete",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", revAfter,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", again.path("code").asText(), again.toString());
        assertFalse(again.path("data").path("changed").asBoolean());
        assertEquals(revAfter, again.path("data").path("resourceRevision").asLong());

        // opposite terminal → STATE_CONFLICT
        JsonNode skip = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/skip",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", revAfter,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("reason", "too late")));
        assertEquals("STATE_CONFLICT", skip.path("code").asText(), skip.toString());

        // stale expectedRevision → REVISION_CONFLICT
        PendingInstance other = createPendingS02("A05 revision");
        JsonNode stale = post(
                "/api/v1/task-instances/" + other.instanceId() + "/commands/complete",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", other.revision() + 99,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("REVISION_CONFLICT", stale.path("code").asText(), stale.toString());
    }

    @Test
    void a05SkipReasonAndConcurrentComplete() throws Exception {
        PendingInstance pending = createPendingS02("A05 skip");
        JsonNode missingReason = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/skip",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", pending.revision(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("INVALID_REQUEST", missingReason.path("code").asText(), missingReason.toString());

        String tooLong = "x".repeat(501);
        JsonNode longReason = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/skip",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", pending.revision(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of("reason", tooLong)));
        assertEquals("INVALID_REQUEST", longReason.path("code").asText(), longReason.toString());

        PendingInstance race = createPendingS02("A05 race");
        long rev = race.revision();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> c1 = () -> post(
                    "/api/v1/task-instances/" + race.instanceId() + "/commands/complete",
                    Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "expectedRevision", rev,
                            "commandSchemaVersion", 1,
                            "payload", Map.of()));
            Callable<JsonNode> c2 = () -> post(
                    "/api/v1/task-instances/" + race.instanceId() + "/commands/complete",
                    Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "expectedRevision", rev,
                            "commandSchemaVersion", 1,
                            "payload", Map.of()));
            Future<JsonNode> f1 = pool.submit(c1);
            Future<JsonNode> f2 = pool.submit(c2);
            JsonNode r1 = f1.get();
            JsonNode r2 = f2.get();
            long changedCount = 0;
            for (JsonNode r : List.of(r1, r2)) {
                if ("OK".equals(r.path("code").asText()) && r.path("data").path("changed").asBoolean()) {
                    changedCount++;
                } else {
                    assertTrue(
                            "REVISION_CONFLICT".equals(r.path("code").asText())
                                    || ("OK".equals(r.path("code").asText())
                                            && !r.path("data").path("changed").asBoolean()),
                            "loser must be NoChange or REVISION_CONFLICT: " + r);
                }
            }
            assertEquals(1, changedCount, "exactly one complete should change; got " + r1 + " / " + r2);

            JsonNode finalInst = get("/api/v1/task-instances/" + race.instanceId());
            assertEquals("COMPLETED", finalInst.path("data").path("scenarioState").asText());
            assertEquals("TERMINAL", finalInst.path("data").path("lifecycleCategory").asText());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a36S02PathAndPauseCancelsWaitingAsCancelled() throws Exception {
        // PLANNED → PENDING → SKIPPED
        PendingInstance pending = createPendingS02("A36 skip path");
        assertEquals("PENDING", pending.scenarioState());
        assertEquals("ACTIVE", pending.lifecycle());
        JsonNode skipped = post(
                "/api/v1/task-instances/" + pending.instanceId() + "/commands/skip",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", pending.revision(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of("reason", "not needed")));
        assertEquals("OK", skipped.path("code").asText(), skipped.toString());
        JsonNode afterSkip = get("/api/v1/task-instances/" + pending.instanceId());
        assertEquals("SKIPPED", afterSkip.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", afterSkip.path("data").path("lifecycleCategory").asText());
        assertNotNull(afterSkip.path("data").path("terminalAt").asText(null));
        assertNotEquals("CANCELLED", afterSkip.path("data").path("scenarioState").asText());

        // pause WAITING → TERMINAL/CANCELLED with terminalAt (not SKIPPED)
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate future = LocalDate.now(zone).plusDays(3);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", future.toString(),
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", "A36 pause cancel",
                        "description", "waiting cancel",
                        "scenarioConfig", Map.of("maxSnoozeCount", 3),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long waitingId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        assertNotNull(waitingId);
        JsonNode beforePause = get("/api/v1/task-instances/" + waitingId);
        assertEquals("PLANNED", beforePause.path("data").path("scenarioState").asText());
        assertEquals("WAITING", beforePause.path("data").path("lifecycleCategory").asText());

        long defRev = created.path("data").path("revision").asLong(1);
        JsonNode paused = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", defRev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", paused.path("code").asText(), paused.toString());

        JsonNode cancelled = get("/api/v1/task-instances/" + waitingId);
        assertEquals("CANCELLED", cancelled.path("data").path("scenarioState").asText(), cancelled.toString());
        assertEquals("TERMINAL", cancelled.path("data").path("lifecycleCategory").asText());
        assertNotNull(cancelled.path("data").path("terminalAt").asText(null));
        assertNotEquals("SKIPPED", cancelled.path("data").path("scenarioState").asText());
    }

    @Test
    void a36S01PlannedToTriggeredAndRejectsS02Commands() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(3).withNano(0);
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
                        "title", "A36 S01",
                        "description", "state path",
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
        assertEquals("PLANNED", before.path("data").path("scenarioState").asText());
        assertEquals("WAITING", before.path("data").path("lifecycleCategory").asText());

        gateway.processSignal(signalId);

        JsonNode after = get("/api/v1/task-instances/" + instanceId);
        assertEquals("TRIGGERED", after.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", after.path("data").path("lifecycleCategory").asText());

        JsonNode complete = post(
                "/api/v1/task-instances/" + instanceId + "/commands/complete",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", after.path("data").path("revision").asLong(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("COMMAND_NOT_SUPPORTED", complete.path("code").asText(), complete.toString());
        assertTrue(
                complete.path("message").asText().contains("complete")
                        && complete.path("message").asText().contains("reminder"),
                complete.toString());
    }

    private PendingInstance createPendingS02(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", title,
                        "description", "A05/A36 helper",
                        "scenarioConfig",
                        Map.of(
                                "chaseOffsetsMinutes", List.of(60, 240),
                                "notificationExpireAfterMinutes", 1440,
                                "maxSnoozeCount", 3),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                instanceId);
        gateway.processSignal(signalId);
        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", inst.path("data").path("scenarioState").asText(), inst.toString());
        return new PendingInstance(
                instanceId,
                definitionId,
                inst.path("data").path("revision").asLong(),
                inst.path("data").path("scenarioState").asText(),
                inst.path("data").path("lifecycleCategory").asText());
    }

    private record PendingInstance(
            long instanceId, long definitionId, long revision, String scenarioState, String lifecycle) {}

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
