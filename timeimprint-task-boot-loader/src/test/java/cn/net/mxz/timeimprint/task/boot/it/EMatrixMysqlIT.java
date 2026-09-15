package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * E01—E13 独立 HTTP 契约矩阵（07 §5）：真 HTTP 信封 + 04 公开读模型字段集；不适用维度在 DELIVERY 标明。
 *
 * <p>阶段：P01 VERIFYING / T08。与纵向 A/M/S smoke 互补，本类按端点覆盖成功、校验、资源、幂等与字段。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class EMatrixMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> PAGE_FIELDS = Set.of("items", "nextCursor", "hasMore", "asOf");
    private static final Set<String> SCENARIO_META_FIELDS = Set.of(
            "scenarioKey",
            "displayName",
            "contractVersion",
            "supportedScenarioSchemaVersions",
            "definitionCommands",
            "instanceCommands",
            "requiredCapabilities");
    private static final Set<String> PREVIEW_FIELDS = Set.of(
            "scenarioKey",
            "scenarioSchemaVersion",
            "normalizedTriggerBindings",
            "normalizedScenarioConfig",
            "occurrences");
    private static final Set<String> OCCURRENCE_FIELDS = Set.of("occurrenceKey", "occurrenceAt", "dueAt");
    private static final Set<String> DEFINITION_FIELDS = Set.of(
            "definitionId",
            "scenarioKey",
            "scenarioSchemaVersion",
            "title",
            "description",
            "controlState",
            "controlGeneration",
            "revision",
            "participants",
            "triggerBindings",
            "scenarioConfig",
            "allowedCommands",
            "createdAt",
            "updatedAt",
            "pausedAt",
            "retiredAt");
    private static final Set<String> INSTANCE_FIELDS = Set.of(
            "instanceId",
            "definitionId",
            "scenarioKey",
            "scenarioSchemaVersion",
            "lifecycleCategory",
            "scenarioState",
            "revision",
            "titleSnapshot",
            "descriptionSnapshot",
            "participants",
            "occurrenceAt",
            "dueAt",
            "allowedCommands",
            "scenarioProjection",
            "deliverySummary",
            "createdAt",
            "updatedAt",
            "terminalAt");
    private static final Set<String> COMMAND_RESULT_FIELDS = Set.of(
            "resourceType", "resourceId", "resourceRevision", "changed", "resourceSnapshot", "scenarioResult");
    private static final Set<String> INBOX_FIELDS = Set.of(
            "inboxId",
            "notificationId",
            "actionJobId",
            "definitionId",
            "instanceId",
            "scenarioKey",
            "purpose",
            "title",
            "body",
            "readAt",
            "createdAt");
    private static final Set<String> UNREAD_FIELDS = Set.of("unreadCount", "asOf");
    private static final Set<String> FORBIDDEN_PUBLIC = Set.of("executionToken", "leaseOwner", "leaseUntil", "payloadHash");

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
    static void props(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        r.add("spring.datasource.username", () -> "tit");
        r.add("spring.datasource.password", () -> "tit_local");
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
        r.add("server.address", () -> "127.0.0.1");
        r.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Test
    void e01ListScenarios() throws Exception {
        JsonNode resp = get("/api/v1/task-scenarios?limit=20");
        assertEquals("OK", resp.path("code").asText(), resp.toString());
        assertNotNull(resp.path("traceId").asText(null));
        JsonNode page = resp.path("data");
        assertFieldSet(page, PAGE_FIELDS);
        assertTrue(page.path("items").size() >= 2, page.toString());
        Set<String> keys = new HashSet<>();
        for (JsonNode item : page.path("items")) {
            assertFieldSet(item, SCENARIO_META_FIELDS);
            assertNoForbiddenPublic(item);
            keys.add(item.path("scenarioKey").asText());
        }
        assertTrue(keys.contains("reminder"), keys.toString());
        assertTrue(keys.contains("recurring_todo"), keys.toString());
    }

    @Test
    void e02PreviewSuccessAndValidation() throws Exception {
        ZonedDateTime occurrence = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(2).withNano(0);
        Map<String, Object> body = previewBody(occurrence, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        JsonNode ok = post("/api/v1/task-definitions/preview", body);
        assertEquals("OK", ok.path("code").asText(), ok.toString());
        JsonNode data = ok.path("data");
        assertFieldSet(data, PREVIEW_FIELDS);
        assertEquals("reminder", data.path("scenarioKey").asText());
        assertTrue(data.path("occurrences").size() >= 1, data.toString());
        assertFieldSet(data.path("occurrences").get(0), OCCURRENCE_FIELDS);
        assertTrue(data.path("occurrences").get(0).path("dueAt").isNull(), data.path("occurrences").get(0).toString());
        assertFalse(data.has("definitionId"));

        long signalsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_signal", Long.class);

        Map<String, Object> badLimit = new LinkedHashMap<>(body);
        badLimit.put("limit", 0);
        JsonNode invalid = post("/api/v1/task-definitions/preview", badLimit);
        assertEquals("INVALID_REQUEST", invalid.path("code").asText(), invalid.toString());

        Map<String, Object> badScenario = new LinkedHashMap<>(body);
        badScenario.put("scenarioKey", "NOT_VALID");
        JsonNode badKey = post("/api/v1/task-definitions/preview", badScenario);
        assertEquals("INVALID_REQUEST", badKey.path("code").asText(), badKey.toString());

        long signalsAfter = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_signal", Long.class);
        assertEquals(signalsBefore, signalsAfter, "E02 must not write signals");
    }

    @Test
    void e03CreateIdempotencyConflictAndValidation() throws Exception {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = createReminderBody(requestId, "E03 " + UUID.randomUUID());
        JsonNode first = post("/api/v1/task-definitions", body);
        assertEquals("OK", first.path("code").asText(), first.toString());
        JsonNode data = first.path("data");
        assertFieldSet(data, DEFINITION_FIELDS);
        assertNoForbiddenPublic(data);
        assertEquals("ACTIVE", data.path("controlState").asText());
        String definitionId = data.path("definitionId").asText();

        JsonNode replay = post("/api/v1/task-definitions", body);
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertEquals(definitionId, replay.path("data").path("definitionId").asText());

        Map<String, Object> conflictBody = new LinkedHashMap<>(body);
        conflictBody.put("title", "E03 conflict title");
        JsonNode conflict = post("/api/v1/task-definitions", conflictBody);
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.path("code").asText(), conflict.toString());

        Map<String, Object> bad = new LinkedHashMap<>(body);
        bad.put("requestId", "not-uuid");
        JsonNode invalid = post("/api/v1/task-definitions", bad);
        assertEquals("INVALID_REQUEST", invalid.path("code").asText(), invalid.toString());
    }

    @Test
    void e04E05GetAndListDefinitions() throws Exception {
        long definitionId = createReminder("E04 " + UUID.randomUUID());
        JsonNode get = get("/api/v1/task-definitions/" + definitionId);
        assertEquals("OK", get.path("code").asText(), get.toString());
        assertFieldSet(get.path("data"), DEFINITION_FIELDS);
        assertNoForbiddenPublic(get.path("data"));
        assertEquals(String.valueOf(definitionId), get.path("data").path("definitionId").asText());

        JsonNode missing = get("/api/v1/task-definitions/999999999");
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());

        JsonNode list = get("/api/v1/task-definitions?scenarioKey=reminder&controlState=ACTIVE&limit=20");
        assertEquals("OK", list.path("code").asText(), list.toString());
        assertFieldSet(list.path("data"), PAGE_FIELDS);
        assertTrue(list.path("data").path("items").size() >= 1, list.toString());
        assertFieldSet(list.path("data").path("items").get(0), DEFINITION_FIELDS);
        boolean found = false;
        for (JsonNode item : list.path("data").path("items")) {
            assertEquals("reminder", item.path("scenarioKey").asText());
            assertEquals("ACTIVE", item.path("controlState").asText());
            if (String.valueOf(definitionId).equals(item.path("definitionId").asText())) {
                found = true;
            }
        }
        assertTrue(found, list.toString());
    }

    @Test
    void e06PauseResumeIdempotencyAndRevisionConflict() throws Exception {
        long definitionId = createReminder("E06 " + UUID.randomUUID());
        long rev = get("/api/v1/task-definitions/" + definitionId).path("data").path("revision").asLong();
        String pauseReq = UUID.randomUUID().toString();
        Map<String, Object> pauseBody = Map.of(
                "requestId", pauseReq, "expectedRevision", rev, "commandSchemaVersion", 1, "payload", Map.of());
        JsonNode paused = post("/api/v1/task-definitions/" + definitionId + "/commands/pause", pauseBody);
        assertEquals("OK", paused.path("code").asText(), paused.toString());
        assertFieldSet(paused.path("data"), COMMAND_RESULT_FIELDS);
        assertEquals("DEFINITION", paused.path("data").path("resourceType").asText());
        assertTrue(paused.path("data").path("changed").asBoolean());
        assertFieldSet(paused.path("data").path("resourceSnapshot"), DEFINITION_FIELDS);
        assertEquals("PAUSED", paused.path("data").path("resourceSnapshot").path("controlState").asText());
        assertNoForbiddenPublic(paused.path("data").path("resourceSnapshot"));

        JsonNode replay = post("/api/v1/task-definitions/" + definitionId + "/commands/pause", pauseBody);
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertEquals(
                paused.path("data").path("resourceRevision").asLong(),
                replay.path("data").path("resourceRevision").asLong());

        long pausedRev = get("/api/v1/task-definitions/" + definitionId).path("data").path("revision").asLong();
        JsonNode stale = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/resume",
                Map.of(
                        "requestId",
                        UUID.randomUUID().toString(),
                        "expectedRevision",
                        rev,
                        "commandSchemaVersion",
                        1,
                        "payload",
                        Map.of()));
        assertEquals("REVISION_CONFLICT", stale.path("code").asText(), stale.toString());

        JsonNode resumed = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/resume",
                Map.of(
                        "requestId",
                        UUID.randomUUID().toString(),
                        "expectedRevision",
                        pausedRev,
                        "commandSchemaVersion",
                        1,
                        "payload",
                        Map.of()));
        assertEquals("OK", resumed.path("code").asText(), resumed.toString());
        assertEquals("ACTIVE", resumed.path("data").path("resourceSnapshot").path("controlState").asText());
    }

    @Test
    void e07E08E09InstanceGetListComplete() throws Exception {
        ZonedDateTime occurrence = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(30).withNano(0);
        Map<String, Object> binding = Map.of(
                "bindingKey",
                "primary",
                "providerKey",
                "calendar",
                "schemaVersion",
                1,
                "config",
                Map.of(
                        "type",
                        "ONCE",
                        "localDate",
                        occurrence.toLocalDate().toString(),
                        "localTime",
                        TIME_FMT.format(occurrence.toLocalTime()),
                        "zoneId",
                        "Asia/Shanghai"));
        Map<String, Object> createBody = Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "scenarioKey",
                "recurring_todo",
                "scenarioSchemaVersion",
                1,
                "title",
                "E09 " + UUID.randomUUID(),
                "description",
                "E matrix",
                "scenarioConfig",
                Map.of(
                        "chaseOffsetsMinutes",
                        List.of(60, 240, 720),
                        "notificationExpireAfterMinutes",
                        1440,
                        "maxSnoozeCount",
                        3),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(binding));
        JsonNode created = post("/api/v1/task-definitions", createBody);
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        gateway.processSignal(signalId);

        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("OK", inst.path("code").asText(), inst.toString());
        assertFieldSet(inst.path("data"), INSTANCE_FIELDS);
        assertNoForbiddenPublic(inst.path("data"));
        assertEquals("PENDING", inst.path("data").path("scenarioState").asText());
        assertTrue(inst.path("data").path("deliverySummary").isObject());

        JsonNode missing = get("/api/v1/task-instances/999999999");
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());

        JsonNode list = get("/api/v1/task-instances?definitionId="
                + definitionId
                + "&scenarioKey=recurring_todo&lifecycleCategory=ACTIVE&limit=20");
        assertEquals("OK", list.path("code").asText(), list.toString());
        assertFieldSet(list.path("data"), PAGE_FIELDS);
        assertTrue(list.path("data").path("items").size() >= 1, list.toString());
        assertFieldSet(list.path("data").path("items").get(0), INSTANCE_FIELDS);

        long rev = inst.path("data").path("revision").asLong();
        String completeReq = UUID.randomUUID().toString();
        Map<String, Object> completeBody = Map.of(
                "requestId", completeReq, "expectedRevision", rev, "commandSchemaVersion", 1, "payload", Map.of());
        JsonNode completed = post("/api/v1/task-instances/" + instanceId + "/commands/complete", completeBody);
        assertEquals("OK", completed.path("code").asText(), completed.toString());
        assertFieldSet(completed.path("data"), COMMAND_RESULT_FIELDS);
        assertEquals("INSTANCE", completed.path("data").path("resourceType").asText());
        assertTrue(completed.path("data").path("changed").asBoolean());
        assertEquals("COMPLETED", completed.path("data").path("resourceSnapshot").path("scenarioState").asText());

        JsonNode replay = post("/api/v1/task-instances/" + instanceId + "/commands/complete", completeBody);
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertEquals(
                completed.path("data").path("resourceRevision").asLong(),
                replay.path("data").path("resourceRevision").asLong());

        // 另建 PENDING 实例测未知 commandKey（终态上会先撞 revision/state）
        Map<String, Object> createBody2 = new LinkedHashMap<>(createBody);
        createBody2.put("requestId", UUID.randomUUID().toString());
        createBody2.put("title", "E09 unknown " + UUID.randomUUID());
        JsonNode created2 = post("/api/v1/task-definitions", createBody2);
        assertEquals("OK", created2.path("code").asText(), created2.toString());
        long def2 = Long.parseLong(created2.path("data").path("definitionId").asText());
        Long instance2 = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, def2);
        Long signal2 = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, def2);
        gateway.processSignal(signal2);
        long rev2 = get("/api/v1/task-instances/" + instance2).path("data").path("revision").asLong();
        JsonNode unknownCmd = post(
                "/api/v1/task-instances/" + instance2 + "/commands/not-a-real-command",
                Map.of(
                        "requestId",
                        UUID.randomUUID().toString(),
                        "expectedRevision",
                        rev2,
                        "commandSchemaVersion",
                        1,
                        "payload",
                        Map.of()));
        assertEquals("COMMAND_NOT_SUPPORTED", unknownCmd.path("code").asText(), unknownCmd.toString());
    }

    @Test
    void e10E11E12E13InboxListDetailUnreadMarkRead() throws Exception {
        long definitionId = createReminder("E10 " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        gateway.processSignal(signalId);
        jdbc.update(
                """
                UPDATE tt_action_job SET available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND
                WHERE definition_id = ? AND status = 'READY'
                """,
                definitionId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status = 'READY' ORDER BY action_job_id ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        assertNotNull(actionJobId);
        actionWorker.executeAction(actionJobId);

        JsonNode list = get("/api/v1/inbox?unreadOnly=true&limit=50");
        assertEquals("OK", list.path("code").asText(), list.toString());
        assertFieldSet(list.path("data"), PAGE_FIELDS);
        assertTrue(list.path("data").path("items").size() >= 1, list.toString());
        JsonNode item = null;
        for (JsonNode candidate : list.path("data").path("items")) {
            if (String.valueOf(definitionId).equals(candidate.path("definitionId").asText())) {
                item = candidate;
                break;
            }
        }
        assertNotNull(item, list.toString());
        assertFieldSet(item, INBOX_FIELDS);
        assertNoForbiddenPublic(item);
        assertEquals(String.valueOf(instanceId), item.path("instanceId").asText());
        assertTrue(item.path("readAt").isNull());
        String inboxId = item.path("inboxId").asText();

        JsonNode detail = get("/api/v1/inbox/" + inboxId);
        assertEquals("OK", detail.path("code").asText(), detail.toString());
        assertFieldSet(detail.path("data"), INBOX_FIELDS);

        JsonNode missing = get("/api/v1/inbox/999999999");
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());

        JsonNode unread = get("/api/v1/inbox-unread-count");
        assertEquals("OK", unread.path("code").asText(), unread.toString());
        assertFieldSet(unread.path("data"), UNREAD_FIELDS);
        assertTrue(unread.path("data").path("unreadCount").asInt() >= 1);

        String markReq = UUID.randomUUID().toString();
        JsonNode marked = post("/api/v1/inbox/" + inboxId + "/commands/mark-read", Map.of("requestId", markReq));
        assertEquals("OK", marked.path("code").asText(), marked.toString());
        assertFieldSet(marked.path("data"), INBOX_FIELDS);
        assertFalse(marked.path("data").path("readAt").isNull());
        String readAt = marked.path("data").path("readAt").asText();

        JsonNode replay = post("/api/v1/inbox/" + inboxId + "/commands/mark-read", Map.of("requestId", markReq));
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertEquals(readAt, replay.path("data").path("readAt").asText());

        JsonNode bad = post("/api/v1/inbox/" + inboxId + "/commands/mark-read", Map.of("requestId", "bad"));
        assertEquals("INVALID_REQUEST", bad.path("code").asText(), bad.toString());
    }

    private static void assertFieldSet(JsonNode obj, Set<String> expected) {
        assertTrue(obj.isObject(), obj.toString());
        Set<String> actual = new HashSet<>();
        obj.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "fields=" + actual);
    }

    private static void assertNoForbiddenPublic(JsonNode node) {
        for (String key : FORBIDDEN_PUBLIC) {
            assertFalse(node.has(key), "public view must not contain " + key + ": " + node);
        }
    }

    private Map<String, Object> previewBody(ZonedDateTime occurrence, String after) {
        return Map.of(
                "scenarioKey",
                "reminder",
                "scenarioSchemaVersion",
                1,
                "scenarioConfig",
                Map.of(),
                "after",
                after,
                "limit",
                5,
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey",
                        "primary",
                        "providerKey",
                        "calendar",
                        "schemaVersion",
                        1,
                        "config",
                        Map.of(
                                "type",
                                "ONCE",
                                "localDate",
                                occurrence.toLocalDate().toString(),
                                "localTime",
                                TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId",
                                "Asia/Shanghai"))));
    }

    private Map<String, Object> createReminderBody(String requestId, String title) {
        ZonedDateTime occurrence = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(50).withNano(0);
        return Map.of(
                "requestId",
                requestId,
                "scenarioKey",
                "reminder",
                "scenarioSchemaVersion",
                1,
                "title",
                title,
                "description",
                "E-matrix",
                "scenarioConfig",
                Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey",
                        "primary",
                        "providerKey",
                        "calendar",
                        "schemaVersion",
                        1,
                        "config",
                        Map.of(
                                "type",
                                "ONCE",
                                "localDate",
                                occurrence.toLocalDate().toString(),
                                "localTime",
                                TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId",
                                "Asia/Shanghai"))));
    }

    private long createReminder(String title) throws Exception {
        JsonNode created = post("/api/v1/task-definitions", createReminderBody(UUID.randomUUID().toString(), title));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
