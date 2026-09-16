package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * I01—I07 独立 HTTP 契约矩阵（07 §5）：真 HTTP 信封 + 关键读模型字段；不适用维度在断言注释标明。
 *
 * <p>阶段：P01 VERIFYING / T08。本类补齐内部端点自动化证据（此前 I 系多为 MANUAL 或 gateway 直调）。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class IMatrixMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> SIGNAL_ACCEPTED_FIELDS =
            Set.of("signalId", "duplicated", "processStatus", "receivedAt");
    private static final Set<String> SIGNAL_DIAG_FIELDS = Set.of(
            "signalId",
            "definitionId",
            "instanceId",
            "providerKey",
            "signalKey",
            "schemaVersion",
            "processStatus",
            "attemptCount",
            "maxAttempts",
            "nextAttemptAt",
            "leaseOwner",
            "leaseUntil",
            "resultCode",
            "resultSummary",
            "occurredAt",
            "receivedAt",
            "processedAt",
            "parentSignalId",
            "redriveNo");
    private static final Set<String> ACTION_DIAG_FIELDS = Set.of(
            "actionJobId",
            "definitionId",
            "instanceId",
            "transitionId",
            "handlerKey",
            "executionMode",
            "schemaVersion",
            "targetType",
            "storedStatus",
            "effectiveStatus",
            "attemptCount",
            "maxAttempts",
            "availableAt",
            "expiresAt",
            "nextAttemptAt",
            "leaseOwner",
            "leaseUntil",
            "outcomeCode",
            "outcomeSummary",
            "completedAt",
            "parentActionJobId",
            "redriveNo",
            "attempts");
    private static final Set<String> TRANSITION_DIAG_FIELDS = Set.of(
            "transitionId",
            "definitionId",
            "instanceId",
            "sourceType",
            "sourceKey",
            "commandKey",
            "fromControlState",
            "toControlState",
            "fromLifecycle",
            "toLifecycle",
            "fromScenarioState",
            "toScenarioState",
            "fromRevision",
            "toRevision",
            "actorType",
            "actorId",
            "traceId",
            "createdAt");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

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
    }

    @Test
    void i01AcceptSuccessReplayConflictAndValidation() throws Exception {
        long definitionId = createOnceReminder("I01 " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        String signalKey = "i01-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        String occurredAt = Instant.now().minusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();

        Map<String, Object> body = i01Body(requestId, signalKey, occurredAt, definitionId, instanceId, Map.of("k", 1));
        JsonNode first = post("/internal/v1/task-signals/event", body);
        assertEquals("OK", first.path("code").asText(), first.toString());
        assertNotNull(first.path("traceId").asText(null));
        JsonNode data = first.path("data");
        assertFieldSet(data, SIGNAL_ACCEPTED_FIELDS);
        assertFalse(data.path("duplicated").asBoolean());
        assertEquals("READY", data.path("processStatus").asText());
        String signalId = data.path("signalId").asText();

        // 同 key 同摘要 → duplicated=true（业务去重；requestId 另计）
        JsonNode replay = post(
                "/internal/v1/task-signals/event",
                i01Body(UUID.randomUUID().toString(), signalKey, occurredAt, definitionId, instanceId, Map.of("k", 1)));
        assertEquals("OK", replay.path("code").asText(), replay.toString());
        assertTrue(replay.path("data").path("duplicated").asBoolean());
        assertEquals(signalId, replay.path("data").path("signalId").asText());

        // 同 key 不同摘要 → IDEMPOTENCY_CONFLICT
        JsonNode conflict = post(
                "/internal/v1/task-signals/event",
                i01Body(UUID.randomUUID().toString(), signalKey, occurredAt, definitionId, instanceId, Map.of("k", 2)));
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.path("code").asText(), conflict.toString());

        // 缺必填 / 非法 UUID → INVALID_REQUEST（Bean Validation）
        Map<String, Object> bad = new LinkedHashMap<>(body);
        bad.put("requestId", "not-a-uuid");
        JsonNode invalid = post("/internal/v1/task-signals/event", bad);
        assertEquals("INVALID_REQUEST", invalid.path("code").asText(), invalid.toString());

        // 未知定义 → RESOURCE_NOT_FOUND
        JsonNode missing = post(
                "/internal/v1/task-signals/event",
                i01Body(
                        UUID.randomUUID().toString(),
                        "i01-missing-" + UUID.randomUUID(),
                        occurredAt,
                        9_999_999_999L,
                        null,
                        Map.of()));
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());

        // 畸形时间：Instant.parse 失败 → 当前落入通用错误（DELIVERY 记为时间格式维度 PARTIAL）
        Map<String, Object> badTime = i01Body(
                UUID.randomUUID().toString(),
                "i01-badtime-" + UUID.randomUUID(),
                "not-an-instant",
                definitionId,
                instanceId,
                Map.of());
        JsonNode timeResp = post("/internal/v1/task-signals/event", badTime);
        assertNotEquals("OK", timeResp.path("code").asText(), timeResp.toString());
    }

    @Test
    void i02GetSignalDiagnosticFieldsAndNoSecrets() throws Exception {
        long definitionId = createOnceReminder("I02 " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long calendarSignalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1", Long.class, definitionId);
        assertNotNull(calendarSignalId);

        JsonNode resp = get("/internal/v1/task-signals/" + calendarSignalId);
        assertEquals("OK", resp.path("code").asText(), resp.toString());
        JsonNode data = resp.path("data");
        assertFieldSet(data, SIGNAL_DIAG_FIELDS);
        assertEquals(String.valueOf(definitionId), data.path("definitionId").asText());
        assertEquals(String.valueOf(instanceId), data.path("instanceId").asText());
        assertEquals("calendar", data.path("providerKey").asText());
        assertFalse(data.has("executionToken"), "must not leak executionToken");
        assertFalse(data.has("payload"), "must not leak payload");
        assertFalse(data.has("payloadHash"), "must not leak payloadHash");
        assertFalse(data.has("payload_json"), data.toString());

        JsonNode missing = get("/internal/v1/task-signals/999999999");
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());
    }

    @Test
    void i03I04ActionJobsListAndDetail() throws Exception {
        long definitionId = createOnceReminder("I03 " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE definition_id = ? AND status = 'READY' LIMIT 1
                """,
                Long.class,
                definitionId);
        assertNotNull(actionJobId);

        JsonNode list = get("/internal/v1/action-jobs?definitionId=" + definitionId + "&limit=20");
        assertEquals("OK", list.path("code").asText(), list.toString());
        assertTrue(list.path("data").path("items").isArray());
        assertTrue(list.path("data").path("items").size() >= 1, list.toString());
        assertFieldSet(list.path("data").path("items").get(0), ACTION_DIAG_FIELDS);

        JsonNode byStatus = get("/internal/v1/action-jobs?definitionId="
                + definitionId
                + "&status=READY&handlerKey=in_app_notification&limit=20");
        assertEquals("OK", byStatus.path("code").asText(), byStatus.toString());
        assertTrue(byStatus.path("data").path("items").size() >= 1, byStatus.toString());
        assertEquals(
                "in_app_notification",
                byStatus.path("data").path("items").get(0).path("handlerKey").asText());

        JsonNode detail = get("/internal/v1/action-jobs/" + actionJobId);
        assertEquals("OK", detail.path("code").asText(), detail.toString());
        JsonNode data = detail.path("data");
        assertFieldSet(data, ACTION_DIAG_FIELDS);
        assertEquals(String.valueOf(actionJobId), data.path("actionJobId").asText());
        assertEquals(String.valueOf(instanceId), data.path("instanceId").asText());
        assertEquals("READY", data.path("storedStatus").asText());
        assertEquals("READY", data.path("effectiveStatus").asText());
        assertTrue(data.path("attempts").isArray());
        assertFalse(data.has("executionToken"));
        assertFalse(data.has("payload"));
        assertFalse(data.has("payloadHash"));
        // leaseOwner/leaseUntil 允许在诊断视图出现（04）；RUNNING 外可为 null
        assertTrue(data.has("leaseOwner"));
        assertTrue(data.has("leaseUntil"));
    }

    @Test
    void i05ListTransitions() throws Exception {
        long definitionId = createOnceReminder("I05 " + UUID.randomUUID());
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        gateway.processSignal(signalId);

        JsonNode list = get("/internal/v1/task-transitions?definitionId=" + definitionId + "&limit=20");
        assertEquals("OK", list.path("code").asText(), list.toString());
        assertTrue(list.path("data").path("items").size() >= 1, list.toString());
        JsonNode item = list.path("data").path("items").get(0);
        assertFieldSet(item, TRANSITION_DIAG_FIELDS);
        assertEquals(String.valueOf(definitionId), item.path("definitionId").asText());

        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        JsonNode byInst = get("/internal/v1/task-transitions?instanceId=" + instanceId + "&limit=20");
        assertEquals("OK", byInst.path("code").asText(), byInst.toString());
        assertTrue(byInst.path("data").path("items").size() >= 1, byInst.toString());
    }

    @Test
    void i06I07RedriveOverHttp() throws Exception {
        long definitionId = createOnceReminder("I06I07 " + UUID.randomUUID());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ?", Long.class, definitionId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND execution_mode = 'LOCAL_TRANSACTIONAL' AND status = 'READY'
                ORDER BY action_job_id DESC LIMIT 1
                """,
                Long.class,
                instanceId);

        jdbc.update(
                """
                UPDATE tt_task_signal SET
                  process_status = 'DEAD',
                  result_code = 'TEST',
                  result_summary = 'I06 HTTP',
                  processed_at = UTC_TIMESTAMP(),
                  attempt_count = max_attempts,
                  lease_owner = NULL,
                  lease_until = NULL,
                  execution_token = NULL
                WHERE signal_id = ?
                """,
                signalId);
        String sigReq = UUID.randomUUID().toString();
        JsonNode sigRedrive = post(
                "/internal/v1/task-signals/" + signalId + "/commands/redrive",
                Map.of("requestId", sigReq, "expectedStatus", "DEAD", "reason", "I06 HTTP"));
        assertEquals("OK", sigRedrive.path("code").asText(), sigRedrive.toString());
        assertFieldSet(sigRedrive.path("data"), SIGNAL_DIAG_FIELDS);
        assertEquals(1, sigRedrive.path("data").path("redriveNo").asInt());
        assertEquals("READY", sigRedrive.path("data").path("processStatus").asText());
        String newSignalId = sigRedrive.path("data").path("signalId").asText();

        JsonNode sigReplay = post(
                "/internal/v1/task-signals/" + signalId + "/commands/redrive",
                Map.of("requestId", sigReq, "expectedStatus", "DEAD", "reason", "I06 HTTP"));
        assertEquals("OK", sigReplay.path("code").asText(), sigReplay.toString());
        assertEquals(newSignalId, sigReplay.path("data").path("signalId").asText());

        jdbc.update(
                """
                UPDATE tt_action_job SET
                  status = 'DEAD', outcome_code = 'I07_HTTP', completed_at = UTC_TIMESTAMP(),
                  lease_owner = NULL, lease_until = NULL, execution_token = NULL
                WHERE action_job_id = ?
                """,
                actionJobId);
        String actReq = UUID.randomUUID().toString();
        JsonNode actRedrive = post(
                "/internal/v1/action-jobs/" + actionJobId + "/commands/redrive",
                Map.of("requestId", actReq, "expectedStatus", "DEAD", "reason", "I07 HTTP"));
        assertEquals("OK", actRedrive.path("code").asText(), actRedrive.toString());
        assertFieldSet(actRedrive.path("data"), ACTION_DIAG_FIELDS);
        assertEquals(1, actRedrive.path("data").path("redriveNo").asInt());
        assertEquals("READY", actRedrive.path("data").path("storedStatus").asText());
        String newActionId = actRedrive.path("data").path("actionJobId").asText();
        assertNotEquals(String.valueOf(actionJobId), newActionId);

        JsonNode actReplay = post(
                "/internal/v1/action-jobs/" + actionJobId + "/commands/redrive",
                Map.of("requestId", actReq, "expectedStatus", "DEAD", "reason", "I07 HTTP"));
        assertEquals("OK", actReplay.path("code").asText(), actReplay.toString());
        assertEquals(newActionId, actReplay.path("data").path("actionJobId").asText());

        // EXTERNAL 拒绝（若库中无 EXTERNAL 行则跳过该断言）
        List<Long> externalIds = jdbc.query(
                "SELECT action_job_id FROM tt_action_job WHERE execution_mode = 'EXTERNAL' LIMIT 1",
                (rs, rowNum) -> rs.getLong(1));
        if (!externalIds.isEmpty()) {
            long externalId = externalIds.get(0);
            jdbc.update(
                    """
                    UPDATE tt_action_job SET status = 'DEAD', outcome_code = 'TEST',
                      completed_at = UTC_TIMESTAMP(), lease_owner = NULL, lease_until = NULL, execution_token = NULL
                    WHERE action_job_id = ?
                    """,
                    externalId);
            JsonNode rejected = post(
                    "/internal/v1/action-jobs/" + externalId + "/commands/redrive",
                    Map.of(
                            "requestId",
                            UUID.randomUUID().toString(),
                            "expectedStatus",
                            "DEAD",
                            "reason",
                            "external"));
            assertEquals("STATE_CONFLICT", rejected.path("code").asText(), rejected.toString());
        }
    }

    private static void assertFieldSet(JsonNode obj, Set<String> expected) {
        assertTrue(obj.isObject(), obj.toString());
        Set<String> actual = new java.util.HashSet<>();
        obj.fieldNames().forEachRemaining(actual::add);
        assertEquals(expected, actual, "fields=" + actual);
    }

    private Map<String, Object> i01Body(
            String requestId,
            String signalKey,
            String occurredAt,
            long definitionId,
            Long instanceId,
            Map<String, Object> payload) {
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("definitionId", String.valueOf(definitionId));
        if (instanceId != null) {
            subject.put("instanceId", String.valueOf(instanceId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("signalKey", signalKey);
        body.put("schemaVersion", 1);
        body.put("occurredAt", occurredAt);
        body.put("subject", subject);
        body.put("payload", payload);
        return body;
    }

    private long createOnceReminder(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(45).withNano(0);
        Map<String, Object> body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "I-matrix",
                "scenarioConfig", Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config",
                        Map.of(
                                "type", "ONCE",
                                "localDate", occurrence.toLocalDate().toString(),
                                "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId", "Asia/Shanghai"))));
        JsonNode created = post("/api/v1/task-definitions", body);
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
