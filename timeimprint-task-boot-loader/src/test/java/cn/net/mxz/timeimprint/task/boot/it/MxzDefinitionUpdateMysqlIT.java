package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.ZoneId;
import java.util.HashMap;
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
 * A37 / A24：definition update 完整替换、description=null、NoChange、日历变更升 scheduleGeneration。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzDefinitionUpdateMysqlIT {

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
    void updateFullReplaceNoChangeClearDescriptionAndCalendarBump() throws Exception {
        LocalDate d1 = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3);
        LocalDate d2 = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(5);
        Map<String, Object> config1 = onceConfig(d1, "09:00:00");
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(UUID.randomUUID().toString(), "Update base", "keep-me", config1));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        long rev1 = created.path("data").path("revision").asLong();
        long scheduleGen1 = created.path("data").path("triggerBindings").get(0).path("scheduleGeneration").asLong();

        Long waitingId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? AND lifecycle_category = 'WAITING' LIMIT 1",
                Long.class,
                definitionId);
        String oldOccurrence = jdbc.queryForObject(
                "SELECT occurrence_key FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                waitingId);

        Map<String, Object> samePayload = updatePayload(
                1,
                "Update base",
                "keep-me",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                config1,
                Map.of());
        JsonNode noChange = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev1, samePayload));
        assertEquals("OK", noChange.path("code").asText(), noChange.toString());
        assertFalse(noChange.path("data").path("changed").asBoolean(), noChange.toString());
        assertEquals(rev1, noChange.path("data").path("resourceRevision").asLong());
        assertEquals(
                scheduleGen1,
                get("/api/v1/task-definitions/" + definitionId)
                        .path("data")
                        .path("triggerBindings")
                        .get(0)
                        .path("scheduleGeneration")
                        .asLong());

        Map<String, Object> clearDesc = updatePayload(
                1,
                "Update base",
                null,
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                config1,
                Map.of());
        JsonNode cleared = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev1, clearDesc));
        assertEquals("OK", cleared.path("code").asText(), cleared.toString());
        assertTrue(cleared.path("data").path("changed").asBoolean());
        long rev2 = cleared.path("data").path("resourceRevision").asLong();
        assertEquals(rev1 + 1, rev2);
        JsonNode afterClear = get("/api/v1/task-definitions/" + definitionId);
        assertTrue(
                afterClear.path("data").path("description").isNull()
                        || afterClear.path("data").path("description").asText().isEmpty()
                        || afterClear.path("data").path("description").isMissingNode(),
                afterClear.toString());
        assertEquals(
                scheduleGen1,
                afterClear.path("data").path("triggerBindings").get(0).path("scheduleGeneration").asLong());
        String waitingTitle = jdbc.queryForObject(
                "SELECT title_snapshot FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                waitingId);
        assertEquals("Update base", waitingTitle);
        String waitingDesc = jdbc.queryForObject(
                "SELECT description_snapshot FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                waitingId);
        assertNull(waitingDesc);

        Map<String, Object> titleOnly = updatePayload(
                1,
                "Update renamed",
                null,
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                config1,
                Map.of());
        JsonNode renamed = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev2, titleOnly));
        assertEquals("OK", renamed.path("code").asText(), renamed.toString());
        assertTrue(renamed.path("data").path("changed").asBoolean());
        long rev3 = renamed.path("data").path("resourceRevision").asLong();
        assertEquals(rev2 + 1, rev3);
        assertEquals(
                scheduleGen1,
                get("/api/v1/task-definitions/" + definitionId)
                        .path("data")
                        .path("triggerBindings")
                        .get(0)
                        .path("scheduleGeneration")
                        .asLong());
        assertEquals(
                "Update renamed",
                jdbc.queryForObject(
                        "SELECT title_snapshot FROM tt_task_instance WHERE instance_id = ?",
                        String.class,
                        waitingId));

        Map<String, Object> omitted = new HashMap<>(titleOnly);
        omitted.remove("title");
        JsonNode bad = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev3, omitted));
        assertEquals("INVALID_REQUEST", bad.path("code").asText(), bad.toString());

        Map<String, Object> config2 = onceConfig(d2, "15:30:00");
        Map<String, Object> calendarPayload = updatePayload(
                1,
                "Update renamed",
                null,
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                config2,
                Map.of());
        JsonNode calendared = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev3, calendarPayload));
        assertEquals("OK", calendared.path("code").asText(), calendared.toString());
        assertTrue(calendared.path("data").path("changed").asBoolean());
        long rev4 = calendared.path("data").path("resourceRevision").asLong();
        assertEquals(rev3 + 1, rev4);
        JsonNode afterCal = get("/api/v1/task-definitions/" + definitionId);
        long scheduleGen2 = afterCal.path("data").path("triggerBindings").get(0).path("scheduleGeneration").asLong();
        assertEquals(scheduleGen1 + 1, scheduleGen2);

        String oldLifecycle = jdbc.queryForObject(
                "SELECT lifecycle_category FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                waitingId);
        assertEquals("TERMINAL", oldLifecycle);
        String oldState = jdbc.queryForObject(
                "SELECT scenario_state FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                waitingId);
        assertEquals("CANCELLED", oldState);

        Long newWaiting = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING' AND schedule_generation = ?
                LIMIT 1
                """,
                Long.class,
                definitionId,
                scheduleGen2);
        assertNotEquals(waitingId, newWaiting);
        String newOccurrence = jdbc.queryForObject(
                "SELECT occurrence_key FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                newWaiting);
        assertNotEquals(oldOccurrence, newOccurrence);
        Integer readySignals = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_task_signal
                WHERE definition_id = ? AND process_status = 'READY' AND signal_key LIKE ?
                """,
                Integer.class,
                definitionId,
                "%:sg" + scheduleGen2 + ":%");
        assertTrue(readySignals != null && readySignals >= 1);
    }

    @Test
    void updateDoesNotOverwriteActiveSnapshot() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        var occurrence = java.time.ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime",
                occurrence.toLocalTime().withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                "zoneId", "Asia/Shanghai");
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", "recurring_todo");
        body.put("scenarioSchemaVersion", 1);
        body.put("title", "Active snap");
        body.put("description", "orig-desc");
        body.put("scenarioConfig", Map.of());
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", daily)));
        JsonNode created = post("/api/v1/task-definitions", body);
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long activeId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                activeId);
        gateway.processSignal(signalId);

        JsonNode before = get("/api/v1/task-instances/" + activeId);
        assertEquals("PENDING", before.path("data").path("scenarioState").asText());
        String oldTitle = before.path("data").path("title").asText();
        long rev = get("/api/v1/task-definitions/" + definitionId).path("data").path("revision").asLong();
        long scheduleGen = get("/api/v1/task-definitions/" + definitionId)
                .path("data")
                .path("triggerBindings")
                .get(0)
                .path("scheduleGeneration")
                .asLong();

        Map<String, Object> payload = updatePayload(
                1,
                "Active snap NEW",
                "new-desc",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                daily,
                Map.of());
        JsonNode updated = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/update",
                commandBody(rev, payload));
        assertEquals("OK", updated.path("code").asText(), updated.toString());
        assertTrue(updated.path("data").path("changed").asBoolean());

        JsonNode afterActive = get("/api/v1/task-instances/" + activeId);
        assertEquals(oldTitle, afterActive.path("data").path("title").asText());
        assertEquals("PENDING", afterActive.path("data").path("scenarioState").asText());
        assertEquals(
                scheduleGen,
                get("/api/v1/task-definitions/" + definitionId)
                        .path("data")
                        .path("triggerBindings")
                        .get(0)
                        .path("scheduleGeneration")
                        .asLong());

        Integer waitingUpdated = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING' AND title_snapshot = 'Active snap NEW'
                """,
                Integer.class,
                definitionId);
        assertTrue(waitingUpdated != null && waitingUpdated >= 1);
    }

    private Map<String, Object> onceConfig(LocalDate date, String localTime) {
        return Map.of(
                "type", "ONCE",
                "localDate", date.toString(),
                "localTime", localTime,
                "zoneId", "Asia/Shanghai");
    }

    private Map<String, Object> createBody(String requestId, String title, String description, Map<String, Object> config) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", requestId);
        body.put("scenarioKey", "reminder");
        body.put("scenarioSchemaVersion", 1);
        body.put("title", title);
        body.put("description", description);
        body.put("scenarioConfig", Map.of());
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        return body;
    }

    private Map<String, Object> updatePayload(
            int schemaVersion,
            String title,
            String description,
            List<Map<String, Object>> participants,
            Map<String, Object> calendarConfig,
            Map<String, Object> scenarioConfig) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioSchemaVersion", schemaVersion);
        payload.put("title", title);
        payload.put("description", description);
        payload.put("participants", participants);
        payload.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", calendarConfig)));
        payload.put("scenarioConfig", scenarioConfig);
        return payload;
    }

    private Map<String, Object> commandBody(long expectedRevision, Map<String, Object> payload) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", expectedRevision,
                "commandSchemaVersion", 1,
                "payload", payload);
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
