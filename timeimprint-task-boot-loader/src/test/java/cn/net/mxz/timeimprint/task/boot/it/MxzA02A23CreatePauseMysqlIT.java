package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * A02（创建幂等重放/冲突/并发）与 A23（PAUSED/RETIRED 下 PENDING 命令边界）证据。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA02A23CreatePauseMysqlIT {

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
    void a02CreateReplayConflictAndConcurrentSameRequestId() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = createBody(requestId, "A02 first " + run, onceFutureBinding());
        JsonNode first = post("/api/v1/task-definitions", body);
        assertEquals("OK", first.path("code").asText(), first.toString());
        String definitionId = first.path("data").path("definitionId").asText();

        JsonNode replay = post("/api/v1/task-definitions", body);
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertEquals(definitionId, replay.path("data").path("definitionId").asText());

        Integer defCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_definition WHERE definition_id = ?",
                Integer.class,
                Long.parseLong(definitionId));
        assertEquals(1, defCount);

        Map<String, Object> conflictBody = createBody(requestId, "A02 different title " + run, onceFutureBinding());
        JsonNode conflict = post("/api/v1/task-definitions", conflictBody);
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.path("code").asText(), conflict.toString());

        String concurrentId = UUID.randomUUID().toString();
        String concurrentTitle = "A02 concurrent " + run;
        Map<String, Object> concurrentBody = createBody(concurrentId, concurrentTitle, onceFutureBinding());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> job = () -> post("/api/v1/task-definitions", concurrentBody);
            Future<JsonNode> f1 = pool.submit(job);
            Future<JsonNode> f2 = pool.submit(job);
            JsonNode r1 = settleCreate(f1.get(), concurrentBody);
            JsonNode r2 = settleCreate(f2.get(), concurrentBody);
            assertEquals("OK", r1.path("code").asText(), r1.toString());
            assertEquals("OK", r2.path("code").asText(), r2.toString());
            assertEquals(
                    r1.path("data").path("definitionId").asText(),
                    r2.path("data").path("definitionId").asText());
            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tt_task_definition WHERE title = ?",
                    Integer.class,
                    concurrentTitle);
            assertEquals(1, rows);
        } finally {
            pool.shutdownNow();
        }

        Integer processingLeft = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_command_dedup WHERE process_status = 'PROCESSING' AND request_id IN (?, ?)",
                Integer.class,
                requestId,
                concurrentId);
        assertEquals(0, processingLeft, "no PROCESSING placeholder left after success");
    }

    @Test
    void a23PausedPendingAllowsCompleteRejectsSnoozeAndCancelsWaiting() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                        "zoneId", "Asia/Shanghai"));

        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(UUID.randomUUID().toString(), "A23 pause pending", daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());

        Long pendingId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long waitingId = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? AND instance_id <> ?
                ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId,
                pendingId);
        assertNotNull(pendingId);
        assertNotNull(waitingId);

        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                pendingId);
        gateway.processSignal(signalId);
        JsonNode pendingBefore = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingBefore.path("data").path("scenarioState").asText());

        JsonNode defGet = get("/api/v1/task-definitions/" + definitionId);
        long defRev = defGet.path("data").path("revision").asLong();
        JsonNode paused = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", defRev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", paused.path("code").asText(), paused.toString());

        JsonNode waitingAfter = get("/api/v1/task-instances/" + waitingId);
        assertEquals("CANCELLED", waitingAfter.path("data").path("scenarioState").asText(), waitingAfter.toString());
        assertEquals("TERMINAL", waitingAfter.path("data").path("lifecycleCategory").asText());
        assertNotNull(waitingAfter.path("data").path("terminalAt").asText(null));
        assertNotEquals("SKIPPED", waitingAfter.path("data").path("scenarioState").asText());

        JsonNode pendingAfterPause = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingAfterPause.path("data").path("scenarioState").asText());
        assertEquals("ACTIVE", pendingAfterPause.path("data").path("lifecycleCategory").asText());

        Integer ignoredSignals = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_task_signal
                WHERE definition_id = ? AND instance_id = ? AND process_status = 'IGNORED'
                """,
                Integer.class,
                definitionId,
                waitingId);
        assertTrue(ignoredSignals != null && ignoredSignals >= 1, "future waiting signals ignored");

        long rev = pendingAfterPause.path("data").path("revision").asLong();
        java.time.Instant snoozeUntil =
                java.time.Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JsonNode snooze = post(
                "/api/v1/task-instances/" + pendingId + "/commands/snooze",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("snoozeUntil", snoozeUntil.toString())));
        assertEquals("STATE_CONFLICT", snooze.path("code").asText(), snooze.toString());

        JsonNode completed = post(
                "/api/v1/task-instances/" + pendingId + "/commands/complete",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", completed.path("code").asText(), completed.toString());
        assertTrue(completed.path("data").path("changed").asBoolean());
        JsonNode finalInst = get("/api/v1/task-instances/" + pendingId);
        assertEquals("COMPLETED", finalInst.path("data").path("scenarioState").asText());
    }

    @Test
    void a23RetiredPendingAllowsSkipRejectsSnooze() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(UUID.randomUUID().toString(), "A23 retire pending", daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long pendingId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                pendingId);
        gateway.processSignal(signalId);

        JsonNode defGet = get("/api/v1/task-definitions/" + definitionId);
        JsonNode retired = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/retire",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", defGet.path("data").path("revision").asLong(),
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("OK", retired.path("code").asText(), retired.toString());

        JsonNode pending = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pending.path("data").path("scenarioState").asText());
        long rev = pending.path("data").path("revision").asLong();

        JsonNode snooze = post(
                "/api/v1/task-instances/" + pendingId + "/commands/snooze",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload",
                        Map.of(
                                "snoozeUntil",
                                java.time.Instant.now()
                                        .plusSeconds(1800)
                                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                                        .toString())));
        assertEquals("STATE_CONFLICT", snooze.path("code").asText(), snooze.toString());

        JsonNode skipped = post(
                "/api/v1/task-instances/" + pendingId + "/commands/skip",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", rev,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("reason", "retired settle")));
        assertEquals("OK", skipped.path("code").asText(), skipped.toString());
        assertEquals(
                "SKIPPED",
                get("/api/v1/task-instances/" + pendingId).path("data").path("scenarioState").asText());
    }

    private JsonNode settleCreate(JsonNode first, Map<String, Object> body) throws Exception {
        JsonNode cur = first;
        for (int i = 0; i < 20 && "RETRY_LATER".equals(cur.path("code").asText()); i++) {
            Thread.sleep(50);
            cur = post("/api/v1/task-definitions", body);
        }
        return cur;
    }

    private Map<String, Object> onceFutureBinding() {
        LocalDate future = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        return Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", future.toString(),
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));
    }

    private Map<String, Object> createBody(String requestId, String title, Map<String, Object> trigger) {
        return Map.of(
                "requestId", requestId,
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "A02/A23",
                "scenarioConfig",
                Map.of(
                        "chaseOffsetsMinutes", List.of(60, 240),
                        "notificationExpireAfterMinutes", 1440,
                        "maxSnoozeCount", 3),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(trigger));
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
