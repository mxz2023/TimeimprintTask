package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * P13 B01–B04：收件软删、清空已读、变化帧，以及 inboxCount 不因软删减少。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class InboxSoftDeleteMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Set<String> DELETE_FIELDS = Set.of("inboxId", "deletedAt");
    private static final Set<String> CLEAR_FIELDS = Set.of("clearedCount");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

    @Autowired
    ActionWorker actionWorker;

    private final HttpClient http = new ItHttpFixture();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/db_timeimprint_task?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @Test
    void deleteHidesRowKeepsFirstTimeAndInboxCount() throws Exception {
        Delivered delivered = deliver("B01 " + UUID.randomUUID());
        int inboxCount = instanceSummary(delivered.instanceId()).path("inboxCount").asInt();
        int unreadInbox = instanceSummary(delivered.instanceId()).path("unreadInboxCount").asInt();
        int unread = unreadCount();
        assertTrue(inboxCount >= 1);
        assertTrue(unreadInbox >= 1);
        assertTrue(listed(delivered.inboxId()));

        String requestId = UUID.randomUUID().toString();
        JsonNode deleted = post("/api/v1/inbox/" + delivered.inboxId() + "/commands/delete", Map.of("requestId", requestId));
        assertEquals("OK", deleted.path("code").asText(), deleted.toString());
        assertEquals(DELETE_FIELDS, fieldNames(deleted.path("data")));
        String deletedAt = deleted.path("data").path("deletedAt").asText();
        assertFalse(deletedAt.isBlank());

        JsonNode replay = post("/api/v1/inbox/" + delivered.inboxId() + "/commands/delete", Map.of("requestId", requestId));
        assertEquals(deletedAt, replay.path("data").path("deletedAt").asText(), replay.toString());
        JsonNode again = post(
                "/api/v1/inbox/" + delivered.inboxId() + "/commands/delete",
                Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("OK", again.path("code").asText(), again.toString());
        assertEquals(deletedAt, again.path("data").path("deletedAt").asText());

        assertFalse(listed(delivered.inboxId()));
        JsonNode missing = get("/api/v1/inbox/" + delivered.inboxId());
        assertEquals("RESOURCE_NOT_FOUND", missing.path("code").asText(), missing.toString());
        JsonNode stranger = postAs(
                ItHttpFixture.ACTOR_B_TOKEN,
                "/api/v1/inbox/" + delivered.inboxId() + "/commands/delete",
                Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("RESOURCE_NOT_FOUND", stranger.path("code").asText(), stranger.toString());
        JsonNode bad = post("/api/v1/inbox/" + delivered.inboxId() + "/commands/delete", Map.of("requestId", "bad"));
        assertEquals("INVALID_REQUEST", bad.path("code").asText(), bad.toString());

        assertEquals(inboxCount, instanceSummary(delivered.instanceId()).path("inboxCount").asInt());
        assertEquals(unreadInbox - 1, instanceSummary(delivered.instanceId()).path("unreadInboxCount").asInt());
        assertEquals(unread - 1, unreadCount());
        assertEquals(1, countRows(delivered.inboxId()));
        assertEquals(deletedAt, storedDeletedAt(delivered.inboxId()));
        assertEquals("SUCCEEDED", actionStatus(delivered.actionJobId()));
        assertEquals(1, notificationCount(delivered.instanceId()));
        Integer dedup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_command_dedup WHERE request_id = ? AND process_status = 'COMPLETED'",
                Integer.class,
                requestId);
        assertEquals(1, dedup);
    }

    @Test
    void clearReadRemovesOnlyOwnReadRows() throws Exception {
        Delivered read = deliver("B02-read " + UUID.randomUUID());
        Delivered unread = deliver("B02-unread " + UUID.randomUUID());
        long other = seedReadInboxFor("actor-b");
        JsonNode marked = post(
                "/api/v1/inbox/" + read.inboxId() + "/commands/mark-read",
                Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals("OK", marked.path("code").asText(), marked.toString());
        int unreadBefore = unreadCount();

        String requestId = UUID.randomUUID().toString();
        JsonNode cleared = post("/api/v1/inbox/commands/clear-read", Map.of("requestId", requestId));
        assertEquals("OK", cleared.path("code").asText(), cleared.toString());
        assertEquals(CLEAR_FIELDS, fieldNames(cleared.path("data")));
        int clearedCount = cleared.path("data").path("clearedCount").asInt();
        assertTrue(clearedCount >= 1, cleared.toString());

        JsonNode replay = post("/api/v1/inbox/commands/clear-read", Map.of("requestId", requestId));
        assertEquals(clearedCount, replay.path("data").path("clearedCount").asInt(), replay.toString());
        JsonNode second = post("/api/v1/inbox/commands/clear-read", Map.of("requestId", UUID.randomUUID().toString()));
        assertEquals(0, second.path("data").path("clearedCount").asInt(), second.toString());

        assertFalse(listed(read.inboxId()));
        assertEquals("RESOURCE_NOT_FOUND", get("/api/v1/inbox/" + read.inboxId()).path("code").asText());
        assertTrue(listed(unread.inboxId()));
        assertEquals(unreadBefore, unreadCount());
        assertNotNull(storedDeletedAt(read.inboxId()));
        assertNull(storedDeletedAt(unread.inboxId()));
        assertNull(storedDeletedAt(other));
        assertEquals(1, countRows(read.inboxId()));
    }

    @Test
    void softDeletePushesInboxChangedWithoutTitle() throws Exception {
        Delivered delivered = deliver("B03 " + UUID.randomUUID());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/inbox/events")))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        try (InputStream body = response.body()) {
            String opened = readUntil(body, "inbox-changed", Duration.ofSeconds(5));
            assertFalse(opened.contains("\"title\""));
            post("/api/v1/inbox/" + delivered.inboxId() + "/commands/delete", Map.of("requestId", UUID.randomUUID().toString()));
            String changed = readUntil(body, "inbox-changed", Duration.ofSeconds(5));
            JsonNode event = objectMapper.readTree(dataJson(changed));
            assertFalse(event.has("title"));
            assertFalse(changed.contains("\"title\""));
            Integer unread = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM tt_inbox
                    WHERE tenant_id = 'local-tenant' AND recipient_type = 'USER' AND recipient_id = 'local-actor'
                      AND read_at IS NULL AND deleted_at IS NULL
                    """,
                    Integer.class);
            Long latest = jdbc.queryForObject(
                    """
                    SELECT MAX(inbox_id) FROM tt_inbox
                    WHERE tenant_id = 'local-tenant' AND recipient_type = 'USER' AND recipient_id = 'local-actor'
                      AND deleted_at IS NULL
                    """,
                    Long.class);
            assertEquals(unread == null ? 0 : unread, event.path("unreadCount").asInt(), event.toString());
            if (latest == null) {
                assertTrue(event.path("latestInboxId").isNull(), event.toString());
            } else {
                assertEquals(latest, event.path("latestInboxId").asLong(), event.toString());
            }
            assertTrue(latest == null || latest != delivered.inboxId());
        }
        assertEquals(1, countRows(delivered.inboxId()));
        assertNotNull(storedDeletedAt(delivered.inboxId()));
    }

    private Delivered deliver(String title) throws Exception {
        ZonedDateTime occurrence = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(40).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", title,
                        "description", "",
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
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = created.path("data").path("definitionId").asLong();
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ?", Long.class, instanceId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE instance_id = ? ORDER BY action_job_id ASC LIMIT 1",
                Long.class,
                instanceId);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND
                WHERE action_job_id = ?
                """,
                actionJobId);
        actionWorker.executeAction(actionJobId);
        Long inboxId = jdbc.queryForObject(
                "SELECT inbox_id FROM tt_inbox WHERE action_job_id = ?", Long.class, actionJobId);
        assertNotNull(instanceId);
        assertNotNull(actionJobId);
        assertNotNull(inboxId);
        return new Delivered(instanceId, actionJobId, inboxId);
    }

    private long seedReadInboxFor(String recipientId) {
        jdbc.update(
                """
                INSERT INTO tt_task_definition(
                  tenant_id,scenario_key,scenario_schema_version,title,description,
                  scenario_config_json,scenario_config_hash,control_state,control_generation,revision,
                  created_by,updated_by,created_at,updated_at)
                VALUES('local-tenant','reminder',1,'b02-other',NULL,
                  CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),
                  'ACTIVE',1,1,'local-actor','local-actor',UTC_TIMESTAMP(0),UTC_TIMESTAMP(0))
                """);
        Long definitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO tt_task_instance(
                  definition_id,definition_control_generation,lifecycle_category,scenario_state,
                  scenario_schema_version,scenario_snapshot_json,snapshot_hash,title_snapshot,
                  revision,created_at,updated_at)
                VALUES(?,1,'WAITING','PLANNED',1,CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),'b02',1,
                  UTC_TIMESTAMP(0),UTC_TIMESTAMP(0))
                """,
                definitionId);
        Long instanceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO tt_task_transition(
                  definition_id, instance_id, source_type, source_key,
                  from_revision, to_revision, to_lifecycle, to_scenario_state,
                  actor_type, actor_id, summary_json, trace_id, created_at)
                VALUES(?, ?, 'SIGNAL', ?, 0, 1, 'WAITING', 'PLANNED',
                  'USER', 'local-actor', CAST('{}' AS JSON), ?, UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                "b02-" + UUID.randomUUID(),
                UUID.randomUUID().toString());
        Long transitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String actionKey = "b02-" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO tt_action_job (
                  tenant_id, definition_id, instance_id, transition_id,
                  definition_control_generation, redrive_no,
                  handler_key, action_key, execution_mode, schema_version,
                  target_type, target_id, payload_json, payload_hash,
                  available_at, status, attempt_count, max_attempts, next_attempt_at,
                  outcome_code, completed_at, created_at, updated_at)
                VALUES (
                  'local-tenant', ?, ?, ?,
                  1, 0,
                  'in_app_notification', ?, 'LOCAL_TRANSACTIONAL', 1,
                  'USER', ?, CAST('{}' AS JSON), UNHEX(REPEAT('00', 32)),
                  UTC_TIMESTAMP(0), 'SUCCEEDED', 1, 3, UTC_TIMESTAMP(0),
                  'SEEDED', UTC_TIMESTAMP(0), UTC_TIMESTAMP(0), UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                transitionId,
                actionKey,
                recipientId);
        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE action_key = ?", Long.class, actionKey);
        jdbc.update(
                """
                INSERT INTO tt_notification(
                  tenant_id, definition_id, instance_id, transition_id, title, purpose, created_at)
                VALUES('local-tenant', ?, ?, ?, 'b02', 'REMINDER', UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                transitionId);
        Long notificationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO tt_inbox(
                  tenant_id, notification_id, action_job_id, definition_id, instance_id,
                  recipient_type, recipient_id, read_at, created_at)
                VALUES('local-tenant', ?, ?, ?, ?, 'USER', ?, UTC_TIMESTAMP(0), UTC_TIMESTAMP(0))
                """,
                notificationId,
                actionJobId,
                definitionId,
                instanceId,
                recipientId);
        Long inboxId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return inboxId == null ? 0L : inboxId;
    }

    private boolean listed(long inboxId) throws Exception {
        JsonNode list = get("/api/v1/inbox?limit=100");
        assertEquals("OK", list.path("code").asText(), list.toString());
        for (JsonNode item : list.path("data").path("items")) {
            if (String.valueOf(inboxId).equals(item.path("inboxId").asText())) {
                return true;
            }
        }
        return false;
    }

    private int unreadCount() throws Exception {
        JsonNode unread = get("/api/v1/inbox-unread-count");
        assertEquals("OK", unread.path("code").asText(), unread.toString());
        return unread.path("data").path("unreadCount").asInt();
    }

    private JsonNode instanceSummary(long instanceId) throws Exception {
        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("OK", inst.path("code").asText(), inst.toString());
        return inst.path("data").path("deliverySummary");
    }

    private int countRows(long inboxId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE inbox_id = ?", Integer.class, inboxId);
        return count == null ? 0 : count;
    }

    private String storedDeletedAt(long inboxId) {
        return jdbc.queryForObject(
                "SELECT DATE_FORMAT(deleted_at, '%Y-%m-%dT%H:%i:%sZ') FROM tt_inbox WHERE inbox_id = ?",
                String.class,
                inboxId);
    }

    private String actionStatus(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private int notificationCount(long instanceId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_notification WHERE instance_id = ?", Integer.class, instanceId);
        return count == null ? 0 : count;
    }

    private static Set<String> fieldNames(JsonNode obj) {
        Set<String> names = new HashSet<>();
        obj.propertyNames().forEach(names::add);
        return names;
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        return postAs(null, path, body);
    }

    private JsonNode postAs(String token, String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static String dataJson(String frame) {
        for (String line : frame.split("\n")) {
            if (line.startsWith("data:")) {
                return line.substring("data:".length()).trim();
            }
        }
        throw new AssertionError(frame);
    }

    private static String readUntil(InputStream body, String needle, Duration budget) throws Exception {
        long deadline = System.nanoTime() + budget.toNanos();
        StringBuilder text = new StringBuilder();
        byte[] buffer = new byte[256];
        while (System.nanoTime() < deadline) {
            if (body.available() == 0) {
                Thread.sleep(50);
                continue;
            }
            int n = body.read(buffer);
            if (n < 0) {
                break;
            }
            text.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            if (text.indexOf(needle) >= 0) {
                return text.toString();
            }
        }
        throw new AssertionError("timed out waiting for " + needle + " in " + text);
    }

    private record Delivered(long instanceId, long actionJobId, long inboxId) {}
}
