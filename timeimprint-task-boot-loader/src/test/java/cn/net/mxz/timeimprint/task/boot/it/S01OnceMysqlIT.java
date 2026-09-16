package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class S01OnceMysqlIT {

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
    void s01OnceVerticalLoop() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        // ONCE must be strictly after create; action runs later via ActionWorker when availableAt is due
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(2).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);

        Map<String, Object> triggerBindings = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));

        Map<String, Object> previewBody = Map.of(
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "scenarioConfig", Map.of(),
                "after", ZonedDateTime.now(zone).minusDays(1).toInstant().toString(),
                "limit", 10,
                "triggerBindings", List.of(triggerBindings));
        JsonNode preview = post("/api/v1/task-definitions/preview", previewBody);
        assertEquals("OK", preview.path("code").asText(), preview.toString());
        assertTrue(preview.path("data").path("occurrences").size() >= 1);

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> createBody = Map.of(
                "requestId", requestId,
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", "S01 ONCE IT",
                "description", "vertical loop",
                "scenarioConfig", Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings", List.of(triggerBindings));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), created.toString());
        String definitionId = created.path("data").path("definitionId").asText();

        Integer actionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?",
                Integer.class,
                Long.parseLong(definitionId));
        assertEquals(0, actionCount);

        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?",
                Long.class,
                Long.parseLong(definitionId));
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ?",
                Long.class,
                Long.parseLong(definitionId),
                instanceId);
        assertNotNull(signalId);

        JsonNode defGet = get("/api/v1/task-definitions/" + definitionId);
        assertEquals("OK", defGet.path("code").asText());
        JsonNode instGet = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PLANNED", instGet.path("data").path("scenarioState").asText());

        gateway.processSignal(signalId);

        JsonNode instAfter = get("/api/v1/task-instances/" + instanceId);
        assertEquals("TRIGGERED", instAfter.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", instAfter.path("data").path("lifecycleCategory").asText());

        Integer readyActions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ? AND status = 'READY'",
                Integer.class,
                Long.parseLong(definitionId));
        assertEquals(1, readyActions, "future ONCE leaves LOCAL_TRANSACTIONAL action READY until availableAt");

        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND WHERE definition_id = ?",
                Long.parseLong(definitionId));
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status = 'READY'
                ORDER BY action_job_id ASC LIMIT 1
                """,
                Long.class,
                Long.parseLong(definitionId));
        assertNotNull(actionJobId);
        actionWorker.executeAction(actionJobId);

        Integer actionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ? AND status = 'SUCCEEDED'",
                Integer.class,
                Long.parseLong(definitionId));
        assertEquals(1, actionsAfter);

        Integer inboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?",
                Integer.class,
                Long.parseLong(definitionId));
        assertEquals(1, inboxCount);

        JsonNode inboxPage = get("/api/v1/inbox?unreadOnly=true");
        assertTrue(inboxPage.path("data").path("items").size() >= 1);
        String inboxId = inboxPage.path("data").path("items").get(0).path("inboxId").asText();

        JsonNode unread = get("/api/v1/inbox-unread-count");
        assertTrue(unread.path("data").path("unreadCount").asInt() >= 1);

        assertEquals("OK", get("/api/v1/inbox/" + inboxId).path("code").asText());

        JsonNode marked = post("/api/v1/inbox/" + inboxId + "/commands/mark-read", Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("OK", marked.path("code").asText());
        assertFalse(marked.path("data").path("readAt").isNull());
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
