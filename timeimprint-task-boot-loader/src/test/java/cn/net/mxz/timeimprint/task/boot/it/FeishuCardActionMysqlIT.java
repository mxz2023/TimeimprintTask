package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * P05 T04 · F06—F09：伪造 {@code card.action.trigger} POST 到 {@code /callbacks/v1/feishu/card-action}，
 * 证明完成 / 跳过 / 稍后+1h 走与 HTTP 相同的命令管道，同 event_id 重放不重复生效，revision 冲突给出 toast 且不覆盖。
 * 不依赖真实飞书租户；Worker 轮询在测试配置中关闭，不会真实出站。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class FeishuCardActionMysqlIT {

    private static final String TOKEN = "it-verification-token";
    private static final String OPEN_ID = "ou_it_operator";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PATH = "/callbacks/v1/feishu/card-action";

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

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
        registry.add("timeimprint.notification.delivery-channels[0]", () -> "IN_APP");
        registry.add("timeimprint.notification.delivery-channels[1]", () -> "FEISHU");
        registry.add("timeimprint.notification.feishu.app-id", () -> "cli_it");
        registry.add("timeimprint.notification.feishu.app-secret", () -> "secret_it");
        registry.add("timeimprint.notification.feishu.verification-token", () -> TOKEN);
        registry.add("timeimprint.notification.feishu.recipient-map.local-actor", () -> OPEN_ID);
    }

    // ── F06 ──────────────────────────────────────────────────────────────────

    @Test
    void completeCallbackTerminatesInstanceAndReplayIsIdempotent() throws Exception {
        Pending p = newPendingTodo("F06 complete");
        String eventId = "evt-" + UUID.randomUUID();

        HttpResponse<String> first = callback(card(eventId, OPEN_ID, "complete", p, p.revision(), null));
        assertEquals(200, first.statusCode(), first.body());
        assertEquals("success", toastType(first), first.body());

        JsonNode inst = instance(p.instanceId());
        assertEquals("COMPLETED", inst.path("scenarioState").asText());
        assertEquals("TERMINAL", inst.path("lifecycleCategory").asText());
        long revisionAfter = inst.path("revision").asLong();
        assertTrue(revisionAfter > p.revision());

        HttpResponse<String> replay = callback(card(eventId, OPEN_ID, "complete", p, p.revision(), null));
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals("info", toastType(replay), "replay must not apply twice: " + replay.body());
        JsonNode after = instance(p.instanceId());
        assertEquals(revisionAfter, after.path("revision").asLong());
    }

    // ── F07 ──────────────────────────────────────────────────────────────────

    @Test
    void skipCallbackUsesFixedReason() throws Exception {
        Pending p = newPendingTodo("F07 skip");
        HttpResponse<String> resp =
                callback(card("evt-" + UUID.randomUUID(), OPEN_ID, "skip", p, p.revision(), null));
        assertEquals("success", toastType(resp), resp.body());

        JsonNode inst = instance(p.instanceId());
        assertEquals("SKIPPED", inst.path("scenarioState").asText());
        String snapshot = jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                p.instanceId());
        assertEquals("飞书卡片跳过", objectMapper.readTree(snapshot).path("reason").asText(), snapshot);
    }

    // ── F08 ──────────────────────────────────────────────────────────────────

    @Test
    void snoozeCallbackShiftsToEventTimePlusOneHourAndReplayDoesNotSnoozeTwice() throws Exception {
        Pending p = newPendingTodo("F08 snooze");
        Instant created = Instant.now().minusSeconds(5);
        String eventId = "evt-" + UUID.randomUUID();

        HttpResponse<String> resp =
                callback(card(eventId, OPEN_ID, "snooze", p, p.revision(), created.toEpochMilli()));
        assertEquals("success", toastType(resp), resp.body());

        String snapshotJson = jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                p.instanceId());
        JsonNode snapshot = objectMapper.readTree(snapshotJson);
        Instant expected = created.truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS);
        assertEquals(expected.toString(), snapshot.path("lastSnoozeUntil").asText(), snapshotJson);
        assertEquals(1, snapshot.path("snoozeCount").asInt(), snapshotJson);
        assertEquals("PENDING", instance(p.instanceId()).path("scenarioState").asText());

        HttpResponse<String> replay =
                callback(card(eventId, OPEN_ID, "snooze", p, p.revision(), created.toEpochMilli()));
        assertEquals("info", toastType(replay), replay.body());
        JsonNode again = objectMapper.readTree(jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                p.instanceId()));
        assertEquals(1, again.path("snoozeCount").asInt(), "replay must not snooze twice");
    }

    // ── F09 ──────────────────────────────────────────────────────────────────

    @Test
    void staleRevisionYieldsWarningToastWithoutOverwriting() throws Exception {
        Pending p = newPendingTodo("F09 conflict");
        HttpResponse<String> resp =
                callback(card("evt-" + UUID.randomUUID(), OPEN_ID, "complete", p, p.revision() + 10, null));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals("warning", toastType(resp), resp.body());
        JsonNode inst = instance(p.instanceId());
        assertEquals("PENDING", inst.path("scenarioState").asText());
        assertEquals(p.revision(), inst.path("revision").asLong());
    }

    // ── guards ───────────────────────────────────────────────────────────────

    @Test
    void forgedTokenIsUnauthorizedAndUnmappedOperatorIsRejected() throws Exception {
        Pending p = newPendingTodo("guards");

        byte[] forged = new String(card("evt-" + UUID.randomUUID(), OPEN_ID, "complete", p, p.revision(), null))
                .replace(TOKEN, "forged-token")
                .getBytes();
        HttpResponse<String> denied = callback(forged);
        assertEquals(401, denied.statusCode(), denied.body());

        HttpResponse<String> stranger =
                callback(card("evt-" + UUID.randomUUID(), "ou_stranger", "complete", p, p.revision(), null));
        assertEquals(200, stranger.statusCode());
        assertEquals("error", toastType(stranger), stranger.body());

        assertEquals("PENDING", instance(p.instanceId()).path("scenarioState").asText());
    }

    @Test
    void reminderScenarioInstanceIsRejectedWithErrorToast() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(5).withNano(0);
        Map<String, Object> binding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                        "zoneId", "Asia/Shanghai"));
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("requestId", UUID.randomUUID().toString());
        create.put("scenarioKey", "reminder");
        create.put("scenarioSchemaVersion", 1);
        create.put("title", "F-S01 reject");
        create.put("description", "s01 has no card buttons");
        create.put("scenarioConfig", Map.of());
        create.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        create.put("triggerBindings", List.of(binding));
        JsonNode created = post("/api/v1/task-definitions", create);
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1", Long.class, definitionId);
        long revision = instance(instanceId).path("revision").asLong();

        HttpResponse<String> resp = callback(card(
                "evt-" + UUID.randomUUID(), OPEN_ID, "complete", new Pending(instanceId, definitionId, revision), revision, null));
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals("error", toastType(resp), resp.body());
        assertEquals(revision, instance(instanceId).path("revision").asLong());
    }

    @Test
    void urlVerificationEchoesChallenge() throws Exception {
        String body = "{\"challenge\":\"it-challenge\",\"token\":\"" + TOKEN + "\",\"type\":\"url_verification\"}";
        HttpResponse<String> resp = callback(body.getBytes());
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals("it-challenge", objectMapper.readTree(resp.body()).path("challenge").asText());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Pending(long instanceId, long definitionId, long revision) {}

    private Pending newPendingTodo(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);
        Map<String, Object> binding = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("requestId", UUID.randomUUID().toString());
        create.put("scenarioKey", "recurring_todo");
        create.put("scenarioSchemaVersion", 1);
        create.put("title", title);
        create.put("description", "feishu card action IT");
        create.put(
                "scenarioConfig",
                Map.of("chaseOffsetsMinutes", List.of(5, 10), "notificationExpireAfterMinutes", 1440, "maxSnoozeCount", 3));
        create.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        create.put("triggerBindings", List.of(binding));
        JsonNode created = post("/api/v1/task-definitions", create);
        assertEquals("OK", created.path("code").asText(), created.toString());
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

        JsonNode inst = instance(instanceId);
        assertEquals("PENDING", inst.path("scenarioState").asText(), inst.toString());
        return new Pending(instanceId, definitionId, inst.path("revision").asLong());
    }

    /** 构造飞书 card.action.trigger（事件 2.0）请求体。 */
    private byte[] card(String eventId, String openId, String commandKey, Pending p, long revision, Long createTimeMillis)
            throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("event_id", eventId);
        header.put("token", TOKEN);
        header.put("event_type", "card.action.trigger");
        header.put("app_id", "cli_it");
        if (createTimeMillis != null) {
            header.put("create_time", String.valueOf(createTimeMillis));
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("commandKey", commandKey);
        value.put("instanceId", p.instanceId());
        value.put("definitionId", p.definitionId());
        value.put("revision", revision);
        Map<String, Object> event = Map.of(
                "operator", Map.of("open_id", openId),
                "action", Map.of("tag", "button", "value", value));
        return objectMapper.writeValueAsBytes(Map.of("schema", "2.0", "header", header, "event", event));
    }

    private HttpResponse<String> callback(byte[] body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String toastType(HttpResponse<String> resp) throws Exception {
        return objectMapper.readTree(resp.body()).path("toast").path("type").asText();
    }

    private JsonNode instance(long instanceId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/task-instances/" + instanceId))
                .GET()
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body()).path("data");
    }

    private JsonNode post(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
