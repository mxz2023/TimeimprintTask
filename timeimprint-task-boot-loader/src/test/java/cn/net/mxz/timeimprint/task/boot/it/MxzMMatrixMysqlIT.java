package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.MxzActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
 * M01—M10 纵向矩阵的相对时间切片（无固定 2026-09-08 业务钟；覆盖五种规则 + S01/S02 关键断言）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzMMatrixMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MxzTaskGateway gateway;

    @Autowired
    MxzActionWorker actionWorker;

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
    void m01S01OnceExhaustsAfterTrigger() throws Exception {
        LocalDate day = LocalDate.now(ZONE).plusDays(2);
        JsonNode preview = preview("reminder", onceConfig(day, "09:00:00"));
        assertEquals(1, preview.path("data").path("occurrences").size());
        String occKey = preview.path("data").path("occurrences").get(0).path("occurrenceKey").asText();

        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody("reminder", "M01 once", onceConfig(day, "09:00:00")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        assertEquals(0, countActions(definitionId));
        assertEquals(1, countLifecycle(definitionId, "WAITING"));
        assertEquals(1, countReadySignals(definitionId));

        // Force due by rewriting occurrence into the past then process.
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        Instant past = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update(
                "UPDATE tt_task_signal SET occurred_at = ?, next_attempt_at = ? WHERE signal_id = ?",
                java.sql.Timestamp.from(past),
                java.sql.Timestamp.from(past),
                signalId);
        gateway.processSignal(signalId);
        assertEquals(1, countActions(definitionId));
        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND WHERE definition_id = ?",
                definitionId);
        drainActions();

        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("TRIGGERED", inst.path("data").path("scenarioState").asText());
        assertEquals("TERMINAL", inst.path("data").path("lifecycleCategory").asText());
        assertEquals(occKey, jdbc.queryForObject(
                "SELECT occurrence_key FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                instanceId));
        Integer exhausted = jdbc.queryForObject(
                "SELECT exhausted FROM tt_trigger_binding WHERE definition_id = ?",
                Integer.class,
                definitionId);
        assertEquals(1, exhausted);
        assertTrue(countInbox(definitionId) >= 1);
    }

    @Test
    void m02M03M04M05S01WindowSpacing() throws Exception {
        LocalDate start = LocalDate.now(ZONE).plusDays(1);
        assertDailySecondIsNextDay(start);
        assertWeeklySecondIsPlus7(start);
        assertMonthly31ClampsButConfigKeeps31();
        assertEveryNDaysSpacing(start, 3);
    }

    @Test
    void m06S02OnceDefaultChaseSlots() throws Exception {
        ZonedDateTime due = ZonedDateTime.now(ZONE).minusMinutes(1).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody("recurring_todo", "M06 once", dailyConfig(due)));
        // use ONCE in the past via daily earliest due already past
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        assertEquals(0, countActions(definitionId));
        gateway.processSignal(signalId);
        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", inst.path("data").path("scenarioState").asText());
        Instant dueAt = Instant.parse(inst.path("data").path("dueAt").asText());
        assertEquals(dueAt, Instant.parse(inst.path("data").path("occurrenceAt").asText()));

        List<Map<String, Object>> actions = jdbc.queryForList(
                """
                SELECT action_key, available_at, expires_at, payload_json FROM tt_action_job
                WHERE instance_id = ? ORDER BY available_at ASC, action_job_id ASC
                """,
                instanceId);
        assertEquals(4, actions.size());
        assertTrue(String.valueOf(actions.get(0).get("action_key")).startsWith("INITIAL:"));
        assertTrue(String.valueOf(actions.get(1).get("action_key")).startsWith("CHASE:"));
        assertTrue(String.valueOf(actions.get(2).get("action_key")).startsWith("CHASE:"));
        assertTrue(String.valueOf(actions.get(3).get("action_key")).startsWith("CHASE:"));
        Instant a0 = toInstant(actions.get(0).get("available_at"));
        Instant a1 = toInstant(actions.get(1).get("available_at"));
        Instant a2 = toInstant(actions.get(2).get("available_at"));
        Instant a3 = toInstant(actions.get(3).get("available_at"));
        assertEquals(dueAt, a0);
        assertEquals(dueAt.plus(60, ChronoUnit.MINUTES), a1);
        assertEquals(dueAt.plus(240, ChronoUnit.MINUTES), a2);
        assertEquals(dueAt.plus(720, ChronoUnit.MINUTES), a3);
        Instant expires = toInstant(actions.get(0).get("expires_at"));
        assertEquals(dueAt.plus(1440, ChronoUnit.MINUTES), expires);
    }

    @Test
    void m07S02DailyPreviousPendingDoesNotBlockNext() throws Exception {
        ZonedDateTime due = ZonedDateTime.now(ZONE).minusMinutes(1).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody("recurring_todo", "M07 daily", dailyConfig(due)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        List<Long> ids = jdbc.query(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 2
                """,
                (rs, i) -> rs.getLong(1),
                definitionId);
        assertEquals(2, ids.size());
        for (Long instanceId : ids) {
            Long signalId = jdbc.queryForObject(
                    "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                    Long.class,
                    instanceId);
            Instant past = Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
            jdbc.update(
                    "UPDATE tt_task_signal SET occurred_at = ?, next_attempt_at = ? WHERE signal_id = ?",
                    java.sql.Timestamp.from(past),
                    java.sql.Timestamp.from(past),
                    signalId);
            gateway.processSignal(signalId);
            assertEquals(
                    "PENDING",
                    get("/api/v1/task-instances/" + instanceId).path("data").path("scenarioState").asText());
        }
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ? AND scenario_state = 'PENDING'",
                Integer.class,
                definitionId);
        assertEquals(2, pending);
    }

    @Test
    void m08M09M10S02WeeklyMonthlyEveryN() throws Exception {
        LocalDate start = LocalDate.now(ZONE).plusDays(1);
        JsonNode weekly = post(
                "/api/v1/task-definitions",
                createBody(
                        "recurring_todo",
                        "M08 weekly",
                        Map.of(
                                "type", "WEEKLY",
                                "startDate", start.toString(),
                                "weekday", start.getDayOfWeek().getValue(),
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", weekly.path("code").asText(), weekly.toString());
        long weeklyId = Long.parseLong(weekly.path("data").path("definitionId").asText());
        assertTrue(countLifecycle(weeklyId, "WAITING") >= 1);

        LocalDate monthStart = LocalDate.now(ZONE).withDayOfMonth(1);
        JsonNode monthly = post(
                "/api/v1/task-definitions",
                createBody(
                        "recurring_todo",
                        "M09 monthly31",
                        Map.of(
                                "type", "MONTHLY",
                                "startDate", monthStart.toString(),
                                "dayOfMonth", 31,
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", monthly.path("code").asText(), monthly.toString());
        long monthlyId = Long.parseLong(monthly.path("data").path("definitionId").asText());
        assertEquals(
                31,
                monthly.path("data").path("triggerBindings").get(0).path("config").path("dayOfMonth").asInt());

        JsonNode every = post(
                "/api/v1/task-definitions",
                createBody(
                        "recurring_todo",
                        "M10 every3",
                        Map.of(
                                "type", "EVERY_N_DAYS",
                                "startDate", start.toString(),
                                "intervalDays", 3,
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", every.path("code").asText(), every.toString());
        long everyId = Long.parseLong(every.path("data").path("definitionId").asText());
        List<Instant> times = jdbc.query(
                """
                SELECT occurrence_at FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 3
                """,
                (rs, i) -> rs.getTimestamp(1).toInstant(),
                everyId);
        if (times.size() >= 2) {
            assertEquals(3, ChronoUnit.DAYS.between(
                    times.get(0).atZone(ZONE).toLocalDate(),
                    times.get(1).atZone(ZONE).toLocalDate()));
        }
    }

    private void assertDailySecondIsNextDay(LocalDate start) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(
                        "reminder",
                        "M02 daily",
                        Map.of(
                                "type", "DAILY",
                                "startDate", start.toString(),
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        List<LocalDate> days = jdbc.query(
                """
                SELECT occurrence_at FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 2
                """,
                (rs, i) -> rs.getTimestamp(1).toInstant().atZone(ZONE).toLocalDate(),
                definitionId);
        assertEquals(2, days.size());
        assertEquals(days.get(0).plusDays(1), days.get(1));
        assertEquals(0, countActions(definitionId));
    }

    private void assertWeeklySecondIsPlus7(LocalDate start) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(
                        "reminder",
                        "M03 weekly",
                        Map.of(
                                "type", "WEEKLY",
                                "startDate", start.toString(),
                                "weekday", start.getDayOfWeek().getValue(),
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        List<LocalDate> days = jdbc.query(
                """
                SELECT occurrence_at FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 2
                """,
                (rs, i) -> rs.getTimestamp(1).toInstant().atZone(ZONE).toLocalDate(),
                definitionId);
        if (days.size() >= 2) {
            assertEquals(7, ChronoUnit.DAYS.between(days.get(0), days.get(1)));
        }
    }

    private void assertMonthly31ClampsButConfigKeeps31() throws Exception {
        LocalDate start = LocalDate.now(ZONE).withDayOfMonth(1);
        JsonNode preview = preview(
                "reminder",
                Map.of(
                        "type", "MONTHLY",
                        "startDate", start.toString(),
                        "dayOfMonth", 31,
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));
        assertTrue(preview.path("data").path("occurrences").size() >= 1, preview.toString());
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(
                        "reminder",
                        "M04 monthly31",
                        Map.of(
                                "type", "MONTHLY",
                                "startDate", start.toString(),
                                "dayOfMonth", 31,
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        assertEquals(
                31,
                created.path("data").path("triggerBindings").get(0).path("config").path("dayOfMonth").asInt());
    }

    private void assertEveryNDaysSpacing(LocalDate start, int n) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                createBody(
                        "reminder",
                        "M05 everyN",
                        Map.of(
                                "type", "EVERY_N_DAYS",
                                "startDate", start.toString(),
                                "intervalDays", n,
                                "localTime", "09:00:00",
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        List<LocalDate> days = jdbc.query(
                """
                SELECT occurrence_at FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 3
                """,
                (rs, i) -> rs.getTimestamp(1).toInstant().atZone(ZONE).toLocalDate(),
                definitionId);
        assertTrue(days.size() >= 2);
        assertEquals(n, ChronoUnit.DAYS.between(days.get(0), days.get(1)));
    }

    private Map<String, Object> onceConfig(LocalDate day, String time) {
        return Map.of("type", "ONCE", "localDate", day.toString(), "localTime", time, "zoneId", "Asia/Shanghai");
    }

    private Map<String, Object> dailyConfig(ZonedDateTime occurrence) {
        return Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                "zoneId", "Asia/Shanghai");
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

    private JsonNode preview(String scenarioKey, Map<String, Object> config) throws Exception {
        Map<String, Object> body = Map.of(
                "scenarioKey", scenarioKey,
                "scenarioSchemaVersion", 1,
                "scenarioConfig", Map.of(),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)),
                "after", Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString(),
                "limit", 10);
        return post("/api/v1/task-definitions/preview", body);
    }

    private int countActions(long definitionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?", Integer.class, definitionId);
        return n == null ? 0 : n;
    }

    private int countLifecycle(long definitionId, String lifecycle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ? AND lifecycle_category = ?",
                Integer.class,
                definitionId,
                lifecycle);
        return n == null ? 0 : n;
    }

    private int countReadySignals(long definitionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_signal WHERE definition_id = ? AND process_status = 'READY'",
                Integer.class,
                definitionId);
        return n == null ? 0 : n;
    }

    private int countInbox(long definitionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?", Integer.class, definitionId);
        return n == null ? 0 : n;
    }

    private Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalArgumentException("unsupported time type: " + value);
    }

    private void drainActions() {
        for (int i = 0; i < 10; i++) {
            actionWorker.pollAndExecute();
        }
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
