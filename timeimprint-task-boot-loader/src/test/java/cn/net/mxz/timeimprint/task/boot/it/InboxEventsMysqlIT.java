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
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
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

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
