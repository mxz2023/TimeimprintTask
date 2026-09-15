package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.ActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
 * A17 收件唯一与 mark-read 重放；A18 非法请求拒绝；A21 S02 配置缺省与边界。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A17A18A21MysqlIT {

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
    void a18RejectsUnsupportedSchemaPastOnceAndUnknownConfig() throws Exception {
        LocalDate future = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        Map<String, Object> okBinding = onceBinding(future, "10:00:00");

        Map<String, Object> badSchema = createBody("reminder", 99, "bad schema", Map.of(), okBinding);
        JsonNode schemaResp = post("/api/v1/task-definitions", badSchema);
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", schemaResp.path("code").asText(), schemaResp.toString());

        LocalDate past = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        Map<String, Object> pastOnce = createBody("reminder", 1, "past once", Map.of(), onceBinding(past, "10:00:00"));
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        JsonNode pastResp = post("/api/v1/task-definitions", pastOnce);
        assertEquals("INVALID_REQUEST", pastResp.path("code").asText(), pastResp.toString());
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        assertEquals(before, after);

        Map<String, Object> unknownField = new HashMap<>(onceBinding(future, "10:00:00"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = new HashMap<>((Map<String, Object>) unknownField.get("config"));
        cfg.put("extra", "x");
        unknownField.put("config", cfg);
        JsonNode unknownResp = post(
                "/api/v1/task-definitions",
                createBody("reminder", 1, "unknown cal", Map.of(), unknownField));
        assertEquals("INVALID_REQUEST", unknownResp.path("code").asText(), unknownResp.toString());

        JsonNode reminderCfg = post(
                "/api/v1/task-definitions",
                createBody("reminder", 1, "nonempty cfg", Map.of("x", 1), okBinding));
        assertEquals("INVALID_REQUEST", reminderCfg.path("code").asText(), reminderCfg.toString());
    }

    @Test
    void a21S02DefaultsAndRejectsIllegalCombos() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = dailyBinding(occurrence);

        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody("recurring_todo", 1, "A21 defaults", Map.of(), daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        JsonNode cfg = created.path("data").path("scenarioConfig");
        assertEquals(1440, cfg.path("notificationExpireAfterMinutes").asInt());
        assertEquals(3, cfg.path("maxSnoozeCount").asInt());
        assertEquals(3, cfg.path("chaseOffsetsMinutes").size());
        assertEquals(60, cfg.path("chaseOffsetsMinutes").get(0).asInt());
        assertEquals(240, cfg.path("chaseOffsetsMinutes").get(1).asInt());
        assertEquals(720, cfg.path("chaseOffsetsMinutes").get(2).asInt());

        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        gateway.processSignal(signalId);
        List<Long> actionIds = jdbc.query(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND status IN ('READY','RETRY_WAIT')
                ORDER BY action_job_id ASC
                """,
                (rs, rowNum) -> rs.getLong(1),
                instanceId);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND
                WHERE instance_id = ?
                """,
                instanceId);
        for (Long actionId : actionIds) {
            actionWorker.executeAction(actionId);
        }
        Integer actions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                instanceId);
        assertEquals(4, actions, "1 INITIAL + 3 CHASE");

        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "bad offsets",
                        Map.of("chaseOffsetsMinutes", List.of(100, 50), "notificationExpireAfterMinutes", 1440),
                        daily));
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "dup offsets",
                        Map.of("chaseOffsetsMinutes", List.of(60, 60), "notificationExpireAfterMinutes", 1440),
                        daily));
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "offset too large",
                        Map.of("chaseOffsetsMinutes", List.of(60, 2000), "notificationExpireAfterMinutes", 1440),
                        daily));
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "expire low",
                        Map.of("notificationExpireAfterMinutes", 30),
                        daily));
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "snooze high",
                        Map.of("maxSnoozeCount", 4),
                        daily));
        assertCreateRejected(
                createBody(
                        "recurring_todo",
                        1,
                        "unknown field",
                        Map.of("foo", 1),
                        daily));
        Integer after = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        assertEquals(before, after);
    }

    @Test
    void a17InboxUniqueAndMarkReadReplayKeepsFirstReadAt() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(2).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(
                        "reminder",
                        1,
                        "A17 inbox",
                        Map.of(),
                        onceBinding(occurrence.toLocalDate(), TIME_FMT.format(occurrence.toLocalTime()))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        gateway.processSignal(signalId);
        gateway.processSignal(signalId);

        Integer actionReady = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?",
                Integer.class,
                definitionId);
        assertEquals(1, actionReady);

        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND WHERE definition_id = ?",
                definitionId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status IN ('READY','RETRY_WAIT')
                ORDER BY action_job_id ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        assertNotNull(actionJobId);
        actionWorker.executeAction(actionJobId);
        actionWorker.executeAction(actionJobId);

        Integer inboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?",
                Integer.class,
                definitionId);
        assertEquals(1, inboxCount);

        Long inboxId = jdbc.queryForObject(
                "SELECT inbox_id FROM tt_inbox WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        JsonNode first = post(
                "/api/v1/inbox/" + inboxId + "/commands/mark-read",
                Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("OK", first.path("code").asText(), first.toString());
        String readAt1 = first.path("data").path("readAt").asText();
        assertNotNull(readAt1);
        assertTrue(!readAt1.isBlank());

        JsonNode second = post(
                "/api/v1/inbox/" + inboxId + "/commands/mark-read",
                Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("OK", second.path("code").asText(), second.toString());
        assertEquals(readAt1, second.path("data").path("readAt").asText());
    }

    private void assertCreateRejected(Map<String, Object> body) throws Exception {
        JsonNode resp = post("/api/v1/task-definitions", body);
        assertEquals("INVALID_REQUEST", resp.path("code").asText(), resp.toString());
    }

    private Map<String, Object> onceBinding(LocalDate date, String localTime) {
        return Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", date.toString(),
                        "localTime", localTime,
                        "zoneId", "Asia/Shanghai"));
    }

    private Map<String, Object> dailyBinding(ZonedDateTime occurrence) {
        return Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                        "zoneId", "Asia/Shanghai"));
    }

    private Map<String, Object> createBody(
            String scenarioKey,
            int schemaVersion,
            String title,
            Map<String, Object> scenarioConfig,
            Map<String, Object> binding) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", scenarioKey);
        body.put("scenarioSchemaVersion", schemaVersion);
        body.put("title", title);
        body.put("description", null);
        body.put("scenarioConfig", scenarioConfig);
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put("triggerBindings", List.of(binding));
        return body;
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
}
