package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A34：定义/实例 keyset 分页在并发更新下弱一致；asOf 为响应时间；单次遍历无重复游标循环；首页刷新可收敛。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA34PaginationMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

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
    }

    @Test
    void a34DefinitionKeyset_noDuplicateCursorLoop_asOfPresent_homeRefreshConverges() throws Exception {
        String marker = "A34D-" + UUID.randomUUID();
        List<Long> created = new ArrayList<>();
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        for (int i = 0; i < 8; i++) {
            // Must fall inside create-time 7-day window and after now.
            ZonedDateTime occurrence = ZonedDateTime.now(zone)
                    .plusHours(2L + i)
                    .withSecond(0)
                    .withNano(0);
            JsonNode resp = post(
                    "/api/v1/task-definitions",
                    Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "scenarioKey", "reminder",
                            "scenarioSchemaVersion", 1,
                            "title", marker + "-" + i,
                            "description", "a34",
                            "scenarioConfig", Map.of(),
                            "participants",
                            List.of(Map.of(
                                    "principalType", "USER",
                                    "principalId", "local-actor",
                                    "roleCode", "OWNER")),
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
            assertEquals("OK", resp.path("code").asText(), resp.toString());
            long id = Long.parseLong(resp.path("data").path("definitionId").asText());
            created.add(id);
            // Distinct updated_at for stable initial keyset order (newest first).
            jdbc.update(
                    "UPDATE tt_task_definition SET updated_at = UTC_TIMESTAMP() - INTERVAL ? SECOND WHERE definition_id = ?",
                    8 - i,
                    id);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < 4; i++) {
                long id = created.get(i);
                jdbc.update(
                        """
                        UPDATE tt_task_definition
                        SET title = CONCAT(title, '-touch'), updated_at = UTC_TIMESTAMP(), revision = revision + 1
                        WHERE definition_id = ?
                        """,
                        id);
                Thread.sleep(50);
            }
            return null;
        });

        start.countDown();
        Set<Long> walkIds = new LinkedHashSet<>();
        String cursor = null;
        Instant asOfSample = null;
        int pages = 0;
        while (pages < 20) {
            pages++;
            String path = "/api/v1/task-definitions?limit=2"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = get(path);
            assertEquals("OK", page.path("code").asText(), page.toString());
            JsonNode data = page.path("data");
            assertTrue(data.hasNonNull("asOf"), "asOf required");
            asOfSample = Instant.parse(data.path("asOf").asText());
            for (JsonNode item : data.path("items")) {
                String title = item.path("title").asText();
                if (!title.startsWith(marker)) {
                    continue;
                }
                long id = Long.parseLong(item.path("definitionId").asText());
                assertFalse(walkIds.contains(id), "duplicate definition in single walk: " + id);
                walkIds.add(id);
            }
            if (!data.path("hasMore").asBoolean()) {
                break;
            }
            cursor = data.path("nextCursor").asText(null);
            assertNotNull(cursor);
        }
        pool.shutdownNow();
        assertNotNull(asOfSample);
        assertTrue(pages >= 2);

        // Home refresh converges: eventually see all created ids (weak consistency during race).
        Set<Long> refreshed = new HashSet<>();
        cursor = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            refreshed.clear();
            cursor = null;
            for (int p = 0; p < 20; p++) {
                String path = "/api/v1/task-definitions?limit=2"
                        + (cursor == null ? "" : "&cursor=" + cursor);
                JsonNode page = get(path);
                for (JsonNode item : page.path("data").path("items")) {
                    String title = item.path("title").asText();
                    if (title.startsWith(marker)) {
                        refreshed.add(Long.parseLong(item.path("definitionId").asText()));
                    }
                }
                if (!page.path("data").path("hasMore").asBoolean()) {
                    break;
                }
                cursor = page.path("data").path("nextCursor").asText(null);
            }
            if (refreshed.containsAll(created)) {
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(refreshed.containsAll(created), "home refresh must converge; missing " + diff(created, refreshed));
    }

    @Test
    void a34InstanceKeyset_immutableOccurrence_noDuplicatesUnderConcurrentInsert() throws Exception {
        String marker = "A34I-" + UUID.randomUUID();
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime base = ZonedDateTime.now(zone).plusDays(5).withHour(9).withMinute(0).withSecond(0).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", marker,
                        "description", "a34i",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings",
                        List.of(Map.of(
                                "bindingKey", "primary",
                                "providerKey", "calendar",
                                "schemaVersion", 1,
                                "config",
                                Map.of(
                                        "type", "DAILY",
                                        "startDate", base.toLocalDate().toString(),
                                        "localTime", TIME_FMT.format(base.toLocalTime()),
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());

        Set<Long> walkIds = new LinkedHashSet<>();
        String cursor = null;
        for (int pages = 0; pages < 30; pages++) {
            String path = "/api/v1/task-instances?definitionId=" + definitionId + "&limit=2"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = get(path);
            assertEquals("OK", page.path("code").asText(), page.toString());
            assertTrue(page.path("data").hasNonNull("asOf"));
            for (JsonNode item : page.path("data").path("items")) {
                long id = Long.parseLong(item.path("instanceId").asText());
                assertFalse(walkIds.contains(id), "duplicate instance in walk: " + id);
                walkIds.add(id);
            }
            if (!page.path("data").path("hasMore").asBoolean()) {
                break;
            }
            cursor = page.path("data").path("nextCursor").asText(null);
            assertNotNull(cursor);
        }
        assertFalse(walkIds.isEmpty());
    }

    private static Set<Long> diff(List<Long> expected, Set<Long> actual) {
        Set<Long> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
