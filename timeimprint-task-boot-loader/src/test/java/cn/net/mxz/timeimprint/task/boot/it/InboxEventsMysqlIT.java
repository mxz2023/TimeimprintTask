package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class InboxEventsMysqlIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper objectMapper;

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
    void missingTokenIsJsonUnauthorized() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/inbox/events")))
                .header("Authorization", "Bearer not-a-session")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertEquals("UNAUTHENTICATED", objectMapper.readTree(response.body()).path("code").asText());
        assertFalse(response.body().contains("inbox-changed"));
    }

    @Test
    void streamFollowsOwnInboxAndHeartbeat() throws Exception {
        Integer unread = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_inbox
                WHERE tenant_id = ? AND recipient_type = ? AND recipient_id = ? AND read_at IS NULL
                """,
                Integer.class,
                "local-tenant",
                "USER",
                "local-actor");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/inbox/events")))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
        try (InputStream body = response.body()) {
            String opened = readUntil(body, "inbox-changed", Duration.ofSeconds(5));
            assertFalse(opened.contains("\"title\""));
            JsonNode event = objectMapper.readTree(dataJson(opened));
            assertEquals(unread == null ? 0 : unread, event.path("unreadCount").asInt());
            assertFalse(event.has("title"));
            String heartbeat = readUntil(body, ": ping", Duration.ofSeconds(30));
            assertTrue(heartbeat.contains(": ping"));
        }
    }

    @Test
    void otherProcessInsertReachesOpenStreamWithinPoll() throws Exception {
        long missed = insertInboxWithoutWake();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/inbox/events")))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        try (InputStream body = response.body()) {
            String opened = readUntil(body, "inbox-changed", Duration.ofSeconds(5));
            JsonNode first = objectMapper.readTree(dataJson(opened));
            assertEquals(1, countOf(opened, "inbox-changed"));
            assertTrue(first.path("latestInboxId").asLong() >= missed);
            assertFalse(first.has("title"));
            String quiet = readAvailable(body, Duration.ofSeconds(1));
            assertFalse(quiet.contains("inbox-changed"), quiet);

            int before = first.path("unreadCount").asInt();
            long inserted = insertInboxWithoutWake();
            String changed = readUntil(body, "inbox-changed", Duration.ofSeconds(6));
            JsonNode event = objectMapper.readTree(dataJson(changed));
            assertFalse(event.has("title"));
            assertTrue(event.path("unreadCount").asInt() >= before + 1, event.toString());
            assertTrue(event.path("latestInboxId").asLong() >= inserted, event.toString());
        }

        Integer unread = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_inbox
                WHERE tenant_id = ? AND recipient_type = ? AND recipient_id = ? AND read_at IS NULL
                """,
                Integer.class,
                "local-tenant",
                "USER",
                "local-actor");
        HttpRequest unreadRequest = HttpRequest.newBuilder(URI.create(url("/api/v1/inbox-unread-count")))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> unreadResponse = http.send(unreadRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, unreadResponse.statusCode());
        assertEquals(
                unread == null ? 0 : unread,
                objectMapper.readTree(unreadResponse.body()).path("data").path("unreadCount").asInt());
    }

    /**
     * 直接写入收件，不调用 {@code InboxWake}。对持有连接的进程来说，这与另一 JVM 提交后的可见结果相同。
     */
    private long insertInboxWithoutWake() {
        jdbc.update(
                """
                INSERT INTO tt_task_definition(
                  tenant_id,scenario_key,scenario_schema_version,title,description,
                  scenario_config_json,scenario_config_hash,control_state,control_generation,revision,
                  created_by,updated_by,created_at,updated_at)
                VALUES('local-tenant','reminder',1,'r04',NULL,
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
                VALUES(?,1,'WAITING','PLANNED',1,CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),'r04',1,
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
                "r04-" + UUID.randomUUID(),
                UUID.randomUUID().toString());
        Long transitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String actionKey = "r04-" + UUID.randomUUID();
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
                  'USER', 'local-actor', CAST('{}' AS JSON), UNHEX(REPEAT('00', 32)),
                  UTC_TIMESTAMP(0), 'SUCCEEDED', 1, 3, UTC_TIMESTAMP(0),
                  'SEEDED', UTC_TIMESTAMP(0), UTC_TIMESTAMP(0), UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                transitionId,
                actionKey);
        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE action_key = ?", Long.class, actionKey);
        jdbc.update(
                """
                INSERT INTO tt_notification(
                  tenant_id, definition_id, instance_id, transition_id, title, purpose, created_at)
                VALUES('local-tenant', ?, ?, ?, 'r04', 'REMINDER', UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                transitionId);
        Long notificationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO tt_inbox(
                  tenant_id, notification_id, action_job_id, definition_id, instance_id,
                  recipient_type, recipient_id, created_at)
                VALUES('local-tenant', ?, ?, ?, ?, 'USER', 'local-actor', UTC_TIMESTAMP(0))
                """,
                notificationId,
                actionJobId,
                definitionId,
                instanceId);
        Long inboxId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return inboxId == null ? 0L : inboxId;
    }

    private static String dataJson(String frame) {
        for (String line : frame.split("\n")) {
            if (line.startsWith("data:")) {
                return line.substring("data:".length()).trim();
            }
        }
        throw new AssertionError(frame);
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = text.indexOf(needle, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + needle.length();
        }
    }

    private static String readAvailable(InputStream body, Duration budget) throws Exception {
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
        }
        return text.toString();
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

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
