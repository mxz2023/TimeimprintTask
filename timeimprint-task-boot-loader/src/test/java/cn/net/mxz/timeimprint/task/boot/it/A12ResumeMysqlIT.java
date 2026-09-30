package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A12：暂停跨周期再恢复——暂停区间不补发，resume 从当前时刻后下一合法 occurrence 重建窗口。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(A12ResumeMysqlIT.ClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("mysql-it")
class A12ResumeMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @TestConfiguration
    static class ClockConfig {
        static final AtomicReference<Instant> NOW =
                new AtomicReference<>(Instant.parse("2026-09-08T01:00:00Z"));

        @Bean
        @Primary
        BusinessClock testBusinessClock() {
            return () -> NOW.get().truncatedTo(ChronoUnit.SECONDS);
        }
    }

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
    }

    @BeforeEach
    void resetClock() {
        ClockConfig.NOW.set(Instant.parse("2026-09-08T01:00:00Z"));
    }

    @Test
    void a12PauseAcrossCyclesResumeDoesNotBackfill() throws Exception {
        // Beijing 2026-09-08 09:00 == 01:00Z. DAILY at 09:00 from Sep 8.
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", "2026-09-08",
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai");
        JsonNode created = post("/api/v1/task-definitions", createBody("recurring_todo", "A12 pause", daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        long controlGen1 = created.path("data").path("controlGeneration").asLong();
        assertEquals(1L, controlGen1);

        List<Instant> beforePause = occurrenceTimes(definitionId, "WAITING");
        assertTrue(beforePause.size() >= 3, "need multi-day window before pause: " + beforePause);
        Instant firstOcc = beforePause.get(0);
        Instant secondOcc = beforePause.get(1);

        // Activate first occurrence into PENDING before pause.
        Long firstId = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING'
                ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                firstId);
        gateway.processSignal(signalId);
        assertEquals(
                "PENDING",
                get("/api/v1/task-instances/" + firstId).path("data").path("scenarioState").asText());
        Integer actionsBeforePause = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                firstId);

        JsonNode defBeforePause = get("/api/v1/task-definitions/" + definitionId);
        JsonNode paused = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                commandBody(defBeforePause.path("data").path("revision").asLong()));
        assertEquals("OK", paused.path("code").asText(), paused.toString());
        assertTrue(paused.path("data").path("changed").asBoolean());
        long controlGen2 = paused.path("data").path("resourceSnapshot").path("controlGeneration").asLong();
        assertEquals(2L, controlGen2);
        assertEquals(0, countLifecycle(definitionId, "WAITING"));
        assertEquals(
                "PENDING",
                get("/api/v1/task-instances/" + firstId).path("data").path("scenarioState").asText());

        // Cross two daily cycles while paused (Sep 9 and Sep 10 09:00 Beijing pass).
        ClockConfig.NOW.set(Instant.parse("2026-09-10T02:00:00Z")); // Beijing Sep 10 10:00
        Instant resumeAt = ClockConfig.NOW.get();

        JsonNode defPaused = get("/api/v1/task-definitions/" + definitionId);
        JsonNode resumed = post(
                "/api/v1/task-definitions/" + definitionId + "/commands/resume",
                commandBody(defPaused.path("data").path("revision").asLong()));
        assertEquals("OK", resumed.path("code").asText(), resumed.toString());
        assertTrue(resumed.path("data").path("changed").asBoolean());
        JsonNode afterResume = get("/api/v1/task-definitions/" + definitionId);
        assertEquals("ACTIVE", afterResume.path("data").path("controlState").asText());
        assertEquals(3L, afterResume.path("data").path("controlGeneration").asLong());
        assertTrue(
                afterResume.path("data").path("pausedAt").isNull()
                        || afterResume.path("data").path("pausedAt").isMissingNode()
                        || afterResume.path("data").path("pausedAt").asText().isBlank(),
                afterResume.toString());

        List<Instant> after = occurrenceTimes(definitionId, "WAITING");
        assertTrue(after.size() >= 1, "resume must rebuild future window");
        for (Instant occ : after) {
            assertTrue(
                    occ.isAfter(resumeAt),
                    "WAITING must be strictly after resume instant, got " + occ + " resumeAt=" + resumeAt);
        }
        // Cycles that fell at/before resume must not reappear as WAITING.
        assertTrue(after.stream().noneMatch(t -> !t.isAfter(resumeAt)));
        assertTrue(
                after.stream().noneMatch(t -> t.equals(firstOcc) || t.equals(secondOcc)),
                "paused-interval occurrences must not be backfilled");
        Integer cancelledBacklog = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_task_instance
                WHERE definition_id = ?
                  AND scenario_state = 'CANCELLED'
                  AND occurrence_at <= ?
                """,
                Integer.class,
                definitionId,
                java.sql.Timestamp.from(resumeAt));
        assertTrue(cancelledBacklog != null && cancelledBacklog >= 1);

        // Pre-pause PENDING kept; chase/actions not rebuilt by resume.
        assertEquals(
                "PENDING",
                get("/api/v1/task-instances/" + firstId).path("data").path("scenarioState").asText());
        Integer actionsAfterResume = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                Integer.class,
                firstId);
        assertEquals(actionsBeforePause, actionsAfterResume);

        Long newControlGen = jdbc.queryForObject(
                """
                SELECT MAX(definition_control_generation) FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING'
                """,
                Long.class,
                definitionId);
        assertEquals(3L, newControlGen);
    }

    private List<Instant> occurrenceTimes(long definitionId, String lifecycle) {
        return jdbc.query(
                """
                SELECT occurrence_at FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = ?
                ORDER BY occurrence_at ASC
                """,
                (rs, i) -> {
                    Object v = rs.getObject(1);
                    if (v instanceof java.sql.Timestamp ts) {
                        return ts.toInstant();
                    }
                    if (v instanceof java.time.LocalDateTime ldt) {
                        return ldt.toInstant(java.time.ZoneOffset.UTC);
                    }
                    throw new IllegalStateException("bad time " + v);
                },
                definitionId,
                lifecycle);
    }

    private int countLifecycle(long definitionId, String lifecycle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ? AND lifecycle_category = ?",
                Integer.class,
                definitionId,
                lifecycle);
        return n == null ? 0 : n;
    }

    private Map<String, Object> createBody(String scenarioKey, String title, Map<String, Object> config) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", scenarioKey);
        body.put("scenarioSchemaVersion", 1);
        body.put("title", title);
        body.put("description", null);
        body.put("scenarioConfig", Map.of());
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        return body;
    }

    private Map<String, Object> commandBody(long expectedRevision) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", expectedRevision,
                "commandSchemaVersion", 1,
                "payload", Map.of());
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

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
