package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * A01/A37：E02 非法日历配置拒绝；合法预览与创建 occurrenceKey 一致。
 * definition update / scheduleGeneration 见 MxzDefinitionUpdateMysqlIT。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA01A37CalendarMysqlIT {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

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
    void a37RejectsInvalidCalendarConfigsOnPreviewAndCreate() throws Exception {
        assertPreviewInvalid(Map.of(
                "type", "WEEKLY",
                "startDate", "2026-09-08",
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai")); // missing weekday
        assertPreviewInvalid(Map.of(
                "type", "WEEKLY",
                "startDate", "2026-09-08",
                "weekday", 5,
                "daysOfWeek", List.of("FRIDAY"),
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewInvalid(Map.of(
                "type", "WEEKLY",
                "startDate", "2026-09-08",
                "weekday", 0,
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewInvalid(Map.of(
                "type", "DAILY",
                "startDate", "2026-09-08",
                "localTime", "09:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewInvalid(Map.of(
                "type", "MONTHLY",
                "startDate", "2026-09-01",
                "dayOfMonth", 31,
                "localTime", "09:00:00",
                "zoneId", "UTC"));

        LocalDate future = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(5);
        Map<String, Object> badCreate = createBody(
                UUID.randomUUID().toString(),
                "A37 bad create",
                Map.of(
                        "type", "ONCE",
                        "localDate", future.toString(),
                        "localTime", "10:00:00",
                        "zoneId", "Asia/Shanghai",
                        "extra", "nope"));
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        JsonNode created = post("/api/v1/task-definitions", badCreate);
        assertEquals("INVALID_REQUEST", created.path("code").asText(), created.toString());
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        assertEquals(before, after, "invalid create must not persist definition");
    }

    @Test
    void a37PreviewAndCreateShareOccurrenceKey() throws Exception {
        LocalDate future = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(4);
        Map<String, Object> config = Map.of(
                "type", "ONCE",
                "localDate", future.toString(),
                "localTime", "11:00:00",
                "zoneId", "Asia/Shanghai");
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config", config);

        JsonNode preview = post(
                "/api/v1/task-definitions/preview",
                Map.of(
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "scenarioConfig", Map.of(),
                        "after", java.time.Instant.now().minusSeconds(60).toString(),
                        "limit", 5,
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", preview.path("code").asText(), preview.toString());
        assertTrue(preview.path("data").path("occurrences").size() >= 1);
        String previewKey = preview.path("data").path("occurrences").get(0).path("occurrenceKey").asText();

        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(UUID.randomUUID().toString(), "A37 key match", config));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        String dbKey = jdbc.queryForObject(
                "SELECT occurrence_key FROM tt_task_instance WHERE definition_id = ?",
                String.class,
                definitionId);
        assertEquals(previewKey, dbKey);
    }

    private void assertPreviewInvalid(Map<String, Object> config) throws Exception {
        JsonNode preview = post(
                "/api/v1/task-definitions/preview",
                Map.of(
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "scenarioConfig", Map.of(),
                        "after", java.time.Instant.now().minusSeconds(60).toString(),
                        "limit", 3,
                        "triggerBindings",
                        List.of(Map.of(
                                "bindingKey", "primary",
                                "providerKey", "calendar",
                                "schemaVersion", 1,
                                "config", config))));
        assertEquals("INVALID_REQUEST", preview.path("code").asText(), "config=" + config + " resp=" + preview);
    }

    private Map<String, Object> createBody(String requestId, String title, Map<String, Object> config) {
        return Map.of(
                "requestId", requestId,
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "A01/A37",
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

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
