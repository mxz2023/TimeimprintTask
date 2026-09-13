package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * S02 recurring_todo 垂直循环集成测试（mysql-it）：
 * 创建 DAILY 定义 → 规划器插入 WAITING 实例 + READY 信号
 * → 处理信号 → 实例进入 PENDING → 执行 complete 命令
 * → 实例进入 COMPLETED（TERMINAL）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzS02RecurringTodoMysqlIT {

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
    void s02DailyBasicLoop() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        // Occurrence 1 minute in the past so the signal is immediately due
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);

        Map<String, Object> triggerBinding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));

        // ── 1. Preview ────────────────────────────────────────────────────────
        Map<String, Object> previewBody = Map.of(
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "scenarioConfig",
                Map.of(
                        "chaseOffsetsMinutes", List.of(60, 240),
                        "notificationExpireAfterMinutes", 1440),
                "after", ZonedDateTime.now(zone).minusDays(1).toInstant().toString(),
                "limit", 3,
                "triggerBindings", List.of(triggerBinding));
        JsonNode preview = post("/api/v1/task-definitions/preview", previewBody);
        assertEquals("OK", preview.path("code").asText(), "preview: " + preview);
        assertTrue(preview.path("data").path("occurrences").size() >= 1, "preview occurrences");

        // ── 2. Create definition ──────────────────────────────────────────────
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> createBody = Map.of(
                "requestId", requestId,
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", "S02 DAILY IT",
                "description", "recurring todo basic loop",
                "scenarioConfig",
                Map.of(
                        "chaseOffsetsMinutes", List.of(60, 240),
                        "notificationExpireAfterMinutes", 1440),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(triggerBinding));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), "create: " + created);
        String definitionId = created.path("data").path("definitionId").asText();
        assertNotNull(definitionId, "definitionId");

        // ── 3. Verify instance + signal exist ─────────────────────────────────
        // DAILY creates 7 instances (7-day window); pick the earliest one with a past due time
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                Long.parseLong(definitionId));
        assertNotNull(instanceId, "Expected at least one PLANNED instance");

        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                Long.parseLong(definitionId),
                instanceId);
        assertNotNull(signalId, "Expected a signal for the instance");

        // Verify initial state via API
        JsonNode instBefore = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PLANNED", instBefore.path("data").path("scenarioState").asText(),
                "instance before signal: " + instBefore);
        assertEquals("WAITING", instBefore.path("data").path("lifecycleCategory").asText());

        // ── 4. Process signal → PENDING ───────────────────────────────────────
        gateway.processSignal(signalId);

        JsonNode instAfterSignal = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", instAfterSignal.path("data").path("scenarioState").asText(),
                "instance after signal: " + instAfterSignal);
        assertEquals("ACTIVE", instAfterSignal.path("data").path("lifecycleCategory").asText());

        // At least the INITIAL action job should exist
        Integer actionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                instanceId);
        assertTrue(actionCount != null && actionCount >= 1, "Expected at least 1 action job, got: " + actionCount);

        String snapshotJson = jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                instanceId);
        assertNotNull(snapshotJson);
        JsonNode snapshot = objectMapper.readTree(snapshotJson);
        assertEquals("PENDING", snapshot.path("scenarioState").asText(), snapshotJson);
        assertEquals(0, snapshot.path("snoozeCount").asInt(), snapshotJson);
        assertEquals(1, snapshot.path("actionGeneration").asInt(), snapshotJson);

        // ── 5. Execute complete command → COMPLETED ───────────────────────────
        String completeRequestId = UUID.randomUUID().toString();
        Map<String, Object> completeBody = Map.of(
                "requestId", completeRequestId,
                "expectedRevision", 0,
                "commandSchemaVersion", 1,
                "payload", Map.of("note", "done"));
        JsonNode completed = post("/api/v1/task-instances/" + instanceId + "/commands/complete", completeBody);
        assertEquals("OK", completed.path("code").asText(), "complete command: " + completed);
        assertTrue(completed.path("data").path("changed").asBoolean(), "expected changed=true");

        // Verify final state
        JsonNode instFinal = get("/api/v1/task-instances/" + instanceId);
        assertEquals("COMPLETED", instFinal.path("data").path("scenarioState").asText(),
                "instance final state: " + instFinal);
        assertEquals("TERMINAL", instFinal.path("data").path("lifecycleCategory").asText());

        // Remaining action jobs should be cancelled
        Integer remainingReady = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ? AND status IN ('READY','RETRY_WAIT')",
                Integer.class,
                instanceId);
        assertEquals(0, remainingReady, "Expected all remaining actions cancelled after complete");
    }

    @Test
    void s02SnoozeShiftsReadyActions() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);

        Map<String, Object> triggerBinding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> createBody = Map.of(
                "requestId", requestId,
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", "S02 snooze IT",
                "description", "snooze shifts READY chase actions",
                "scenarioConfig",
                Map.of(
                        "chaseOffsetsMinutes", List.of(5, 10),
                        "notificationExpireAfterMinutes", 1440,
                        "maxSnoozeCount", 3),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(triggerBinding));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), "create: " + created);
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());

        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        assertNotNull(instanceId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                instanceId);
        assertNotNull(signalId);
        gateway.processSignal(signalId);

        Integer readyBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ? AND status IN ('READY','RETRY_WAIT')",
                Integer.class,
                instanceId);
        assertTrue(readyBefore != null && readyBefore >= 1, "need movable actions before snooze");

        java.time.Instant snoozeUntil = java.time.Instant.now().plusSeconds(20 * 60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JsonNode snoozed = post(
                "/api/v1/task-instances/" + instanceId + "/commands/snooze",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", 1,
                        "commandSchemaVersion", 1,
                        "payload", Map.of("snoozeUntil", snoozeUntil.toString())));
        assertEquals("OK", snoozed.path("code").asText(), "snooze: " + snoozed);
        assertTrue(snoozed.path("data").path("changed").asBoolean());
        assertEquals(1, snoozed.path("data").path("scenarioResult").path("snoozeCount").asInt(), snoozed.toString());
        assertEquals(2, snoozed.path("data").path("scenarioResult").path("actionGeneration").asInt(), snoozed.toString());
        assertTrue(snoozed.path("data").path("scenarioResult").path("remainingReminderAts").size() >= 1);

        String snapshotJson = jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                instanceId);
        JsonNode snapshot = objectMapper.readTree(snapshotJson);
        assertEquals(1, snapshot.path("snoozeCount").asInt(), snapshotJson);
        assertEquals(2, snapshot.path("actionGeneration").asInt(), snapshotJson);

        Integer cancelled = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ? AND status = 'CANCELLED'",
                Integer.class,
                instanceId);
        assertTrue(cancelled != null && cancelled >= 1, "old generation should be cancelled");
        Integer readyAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ? AND status IN ('READY','RETRY_WAIT')",
                Integer.class,
                instanceId);
        assertEquals(readyBefore, readyAfter, "same number of movable actions after snooze");
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
