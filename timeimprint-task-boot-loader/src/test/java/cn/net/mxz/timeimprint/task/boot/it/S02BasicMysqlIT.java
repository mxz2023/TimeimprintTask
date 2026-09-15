package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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
 * S02 basic IT: creates a recurring_todo ONCE/DAILY definition, processes the first signal
 * (PLANNED → PENDING), then completes the instance via E09 (PENDING → COMPLETED).
 *
 * Verifies the S02 vertical loop against a real MySQL 9.7.2 instance.
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class S02BasicMysqlIT {

    @LocalServerPort int port;

    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskGateway gateway;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @Test
    void s02RecurringTodoOncePlanToPendingToCompleted() throws Exception {
        // Step 1: Preview — ONCE trigger 2 days from now
        LocalDate futureDate = LocalDate.now().plusDays(2);
        Map<String, Object> triggerBinding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config", Map.of(
                        "type", "ONCE",
                        "localDate", futureDate.toString(),
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));

        Map<String, Object> previewBody = Map.of(
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "scenarioConfig", Map.of(
                        "chaseOffsetsMinutes", List.of(60, 240, 720),
                        "notificationExpireAfterMinutes", 1440,
                        "maxSnoozeCount", 3),
                "after", java.time.Instant.now().minusSeconds(86400).toString(),
                "limit", 5,
                "triggerBindings", List.of(triggerBinding));
        JsonNode preview = post("/api/v1/task-definitions/preview", previewBody);
        assertEquals("OK", preview.path("code").asText(), "preview: " + preview);
        assertTrue(preview.path("data").path("occurrences").size() >= 1,
                "preview must return at least 1 occurrence");

        // Step 2: Create definition
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> createBody = Map.of(
                "requestId", requestId,
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", "S02 ONCE IT - 提交周报",
                "description", "recurring_todo ONCE vertical loop",
                "scenarioConfig", Map.of(
                        "chaseOffsetsMinutes", List.of(60, 240, 720),
                        "notificationExpireAfterMinutes", 1440,
                        "maxSnoozeCount", 3),
                "participants", List.of(
                        Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(triggerBinding));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), "create: " + created);
        String definitionId = created.path("data").path("definitionId").asText();
        assertNotNull(definitionId, "definitionId must be present");

        // Step 3: Verify PLANNED instance exists with WAITING lifecycle
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?",
                Long.class, Long.parseLong(definitionId));
        assertNotNull(instanceId, "An instance must exist immediately after creation");

        JsonNode instGet = get("/api/v1/task-instances/" + instanceId);
        assertEquals("OK", instGet.path("code").asText());
        assertEquals("PLANNED", instGet.path("data").path("scenarioState").asText(),
                "instance must start in PLANNED state");
        assertEquals("WAITING", instGet.path("data").path("lifecycleCategory").asText(),
                "instance must start with WAITING lifecycle");

        // No action jobs yet (signal not yet processed)
        Integer actionsBeforeSignal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?",
                Integer.class, Long.parseLong(definitionId));
        assertEquals(0, actionsBeforeSignal, "No action jobs before signal is processed");

        // Step 4: Get signal and process it → PLANNED becomes PENDING
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ?",
                Long.class, Long.parseLong(definitionId), instanceId);
        assertNotNull(signalId, "A READY signal must exist");

        gateway.processSignal(signalId);

        JsonNode instPending = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", instPending.path("data").path("scenarioState").asText(),
                "instance must be PENDING after signal processing");
        assertEquals("ACTIVE", instPending.path("data").path("lifecycleCategory").asText(),
                "instance must be ACTIVE after signal processing");

        // Step 5: Action jobs created (at least INITIAL notification)
        Integer actionsAfterSignal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?",
                Integer.class, Long.parseLong(definitionId));
        assertTrue(actionsAfterSignal != null && actionsAfterSignal >= 1,
                "At least 1 action job (INITIAL notification) must be created after signal");

        // Step 6: Complete the instance via E09
        String completeRequestId = UUID.randomUUID().toString();
        long instanceRevision = instPending.path("data").path("revision").asLong();
        Map<String, Object> completeBody = Map.of(
                "requestId", completeRequestId,
                "expectedRevision", instanceRevision,
                "commandSchemaVersion", 1,
                "payload", Map.of());
        JsonNode completeResult = post("/api/v1/task-instances/" + instanceId + "/commands/complete", completeBody);
        assertEquals("OK", completeResult.path("code").asText(), "complete: " + completeResult);
        assertTrue(completeResult.path("data").path("changed").asBoolean(),
                "changed must be true after complete");

        // Step 7: Verify COMPLETED terminal state
        JsonNode instCompleted = get("/api/v1/task-instances/" + instanceId);
        assertEquals("COMPLETED", instCompleted.path("data").path("scenarioState").asText(),
                "instance must be COMPLETED after complete command");
        assertEquals("TERMINAL", instCompleted.path("data").path("lifecycleCategory").asText(),
                "instance must be TERMINAL after complete command");
    }

    @Test
    void s02RecurringTodoDailyPlanToPendingToSkipped() throws Exception {
        // DAILY trigger from yesterday so first occurrence is today or next scheduled
        LocalDate startDate = LocalDate.now().minusDays(1);
        Map<String, Object> triggerBinding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config", Map.of(
                        "type", "DAILY",
                        "startDate", startDate.toString(),
                        "localTime", "08:30:00",
                        "zoneId", "Asia/Shanghai"));

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> createBody = Map.of(
                "requestId", requestId,
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", "S02 DAILY IT - 每日站会",
                "description", "recurring_todo DAILY skip test",
                "scenarioConfig", Map.of(),
                "participants", List.of(
                        Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(triggerBinding));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), "create: " + created);
        String definitionId = created.path("data").path("definitionId").asText();

        // DAILY creates multiple instances (7-day window); pick the earliest one
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class, Long.parseLong(definitionId));
        assertNotNull(instanceId);

        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class, Long.parseLong(definitionId), instanceId);
        assertNotNull(signalId);
        gateway.processSignal(signalId);

        JsonNode instPending = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", instPending.path("data").path("scenarioState").asText());

        // Skip the instance via E09
        String skipRequestId = UUID.randomUUID().toString();
        long revision = instPending.path("data").path("revision").asLong();
        Map<String, Object> skipBody = Map.of(
                "requestId", skipRequestId,
                "expectedRevision", revision,
                "commandSchemaVersion", 1,
                "payload", Map.of("reason", "跳过本次每日站会"));
        JsonNode skipResult = post("/api/v1/task-instances/" + instanceId + "/commands/skip", skipBody);
        assertEquals("OK", skipResult.path("code").asText(), "skip: " + skipResult);
        assertTrue(skipResult.path("data").path("changed").asBoolean());

        JsonNode instSkipped = get("/api/v1/task-instances/" + instanceId);
        assertEquals("SKIPPED", instSkipped.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", instSkipped.path("data").path("lifecycleCategory").asText());
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
