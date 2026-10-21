package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerPlannerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/**
 * P14 L01–L08：schemaVersion 3 时间段的拒绝、过滤、升级与规划上限。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(P14TimeWindowMysqlIT.ClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("mysql-it")
class P14TimeWindowMysqlIT {

    private static final Instant NOW = Instant.parse("2026-01-05T00:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        static final AtomicReference<Instant> CLOCK = new AtomicReference<>(NOW);

        @Bean
        @Primary
        BusinessClock testBusinessClock() {
            return () -> CLOCK.get().truncatedTo(ChronoUnit.SECONDS);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TriggerPlannerPort plannerPort;

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

    @BeforeEach
    void resetClock() {
        ClockConfig.CLOCK.set(NOW);
    }

    @Test
    void l02RejectsEachReasonWithoutWritingAndRejectsOtherScenarios() throws Exception {
        assertRejected("TW_FIELD", dailyBody(3, daily("09:30:00", "nope")));
        assertRejected("TW_FIELD", dailyBody(3, daily("09:30:00")));
        assertRejected("TW_MODE", dailyBody(3, daily("09:30:00", window("allow", List.of(seg("09:00", "10:00")), List.of()))));
        assertRejected("TW_TIME_FORMAT", dailyBody(3, daily("09:30:00", window("ALLOW", List.of(seg("24:00", "10:00")), List.of()))));
        assertRejected("TW_TIME_FORMAT", dailyBody(3, daily("09:30:00", window("ALLOW", List.of(seg("09:00", "00:00")), List.of()))));
        assertRejected("TW_ZERO_LENGTH", dailyBody(3, daily("09:30:00", window("ALLOW", List.of(seg("09:00", "09:00")), List.of()))));
        assertRejected(
                "TW_TOO_MANY",
                dailyBody(
                        3,
                        daily(
                                "09:30:00",
                                window(
                                        "ALLOW",
                                        List.of(
                                                seg("00:00", "01:00"),
                                                seg("02:00", "03:00"),
                                                seg("04:00", "05:00"),
                                                seg("06:00", "07:00"),
                                                seg("08:00", "09:00"),
                                                seg("10:00", "11:00"),
                                                seg("12:00", "13:00"),
                                                seg("14:00", "15:00"),
                                                seg("16:00", "17:00")),
                                        List.of()))));
        assertRejected(
                "TW_ORDER",
                dailyBody(3, daily("09:30:00", window("ALLOW", List.of(seg("11:00", "12:00"), seg("09:00", "10:00")), List.of()))));
        assertRejected(
                "TW_OVERLAP",
                dailyBody(3, daily("09:30:00", window("ALLOW", List.of(seg("09:00", "12:00"), seg("11:00", "13:00")), List.of()))));
        assertRejected("TW_ALLOW_EMPTY", dailyBody(3, daily("09:30:00", window("ALLOW", List.of(), List.of()))));
        assertRejected("TW_DENY_EMPTY", dailyBody(3, daily("09:30:00", window("DENY", List.of(), List.of()))));
        assertRejected(
                "TW_NO_OCCURRENCE",
                dailyBody(3, once("2026-01-06", "09:30:00", window("ALLOW", List.of(seg("10:00", "11:00")), List.of()))));

        assertUnsupported("anniversary", anniversaryConfig(), daily("09:00:00"));
        assertUnsupported("deadline", deadlineConfig(), daily("09:00:00"));
        assertUnsupported("payment", paymentConfig(), daily("09:00:00"));
        assertUnsupported("maintenance", maintenanceConfig(), daily("09:00:00"));
    }

    @Test
    void l01NullWindowMatchesSchema1() throws Exception {
        long v1 = createOk("reminder", Map.of(), 1, daily("09:30:00"));
        long v3 = createOk("reminder", Map.of(), 3, daily("09:30:00", null));
        assertEquals(occurrenceKeys(v1), occurrenceKeys(v3));
        assertEquals(1, schemaVersion(v1));
        assertEquals(3, schemaVersion(v3));
    }

    @Test
    void l03l04l06FiltersWorkdayWindowAndPreviewMatches() throws Exception {
        Object allow = window("ALLOW", List.of(seg("09:00", "10:00")), List.of());
        long allowId = createOk("reminder", Map.of(), 3, daily("09:30:00", allow));
        assertEquals(
                List.of(
                        "20260105T013000Z",
                        "20260106T013000Z",
                        "20260107T013000Z",
                        "20260108T013000Z",
                        "20260109T013000Z"),
                occurrenceKeys(allowId));
        assertEquals(occurrenceKeys(allowId).size(), signalCount(allowId));

        Object deny = window("DENY", List.of(seg("09:00", "10:00")), List.of());
        long denyId = createOk("reminder", Map.of(), 3, daily("09:30:00", deny));
        assertEquals(List.of("20260110T013000Z", "20260111T013000Z"), occurrenceKeys(denyId));

        JsonNode preview = post(
                "/api/v1/task-definitions/preview",
                Map.of(
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "scenarioConfig", Map.of(),
                        "after", NOW.toString(),
                        "limit", 20,
                        "triggerBindings",
                        List.of(Map.of(
                                "bindingKey", "primary",
                                "providerKey", "calendar",
                                "schemaVersion", 3,
                                "config", daily("09:30:00", allow)))));
        assertEquals("OK", preview.path("code").asText(), preview.toString());
        List<String> previewKeys = new ArrayList<>();
        preview.path("data").path("occurrences").forEach(node -> previewKeys.add(node.path("occurrenceKey").asText()));
        assertEquals(occurrenceKeys(allowId), previewKeys.subList(0, occurrenceKeys(allowId).size()));
    }

    @Test
    void l07UpgradeNoChangeAndDowngrade() throws Exception {
        long id = createOk("reminder", Map.of(), 1, daily("09:30:00"));
        long revision = revision(id);
        JsonNode upgraded = post(
                "/api/v1/task-definitions/" + id + "/commands/update",
                command(revision, "reminder", "P14", 3, daily("09:30:00", window("ALLOW", List.of(seg("09:00", "10:00")), List.of()))));
        assertEquals("OK", upgraded.path("code").asText(), upgraded.toString());
        assertTrue(upgraded.path("data").path("changed").asBoolean());
        assertEquals(2, scheduleGeneration(id));
        assertEquals(3, schemaVersion(id));
        assertEquals(5, occurrenceKeys(id).size());

        long same = createOk("reminder", Map.of(), 1, daily("11:00:00"));
        long sameRev = revision(same);
        long gen = scheduleGeneration(same);
        JsonNode noChange = post(
                "/api/v1/task-definitions/" + same + "/commands/update",
                command(sameRev, "reminder", "P14", 3, daily("11:00:00", null)));
        assertEquals("OK", noChange.path("code").asText(), noChange.toString());
        assertFalse(noChange.path("data").path("changed").asBoolean(), noChange.toString());
        assertEquals(sameRev, revision(same));
        assertEquals(gen, scheduleGeneration(same));
        assertEquals(1, schemaVersion(same));

        long downgrade = createOk("reminder", Map.of(), 3, daily("09:30:00", window("ALLOW", List.of(seg("09:00", "10:00")), List.of())));
        int before = countDefinitions();
        JsonNode rejected = post(
                "/api/v1/task-definitions/" + downgrade + "/commands/update",
                command(revision(downgrade), "reminder", "P14", 1, daily("09:30:00")));
        assertEquals("INVALID_REQUEST", rejected.path("code").asText(), rejected.toString());
        assertTrue(rejected.path("message").asText().startsWith("TW_DOWNGRADE"), rejected.toString());
        assertEquals(before, countDefinitions());
        assertEquals(3, schemaVersion(downgrade));
    }

    @Test
    void l08CapsSyncWritesAndDoesNotExhaustALaterHit() throws Exception {
        Object allDay = window("ALLOW", List.of(seg("00:00", "24:00")), List.of(seg("00:00", "24:00")));
        long dense = createOk("reminder", Map.of(), 3, minutes(1, allDay));
        assertEquals(100, occurrenceKeys(dense).size());
        assertEquals(0, exhausted(dense));
        long bindingId = bindingId(dense);
        int afterFirst = occurrenceKeys(dense).size();
        plannerPort.planBinding(bindingId, NOW, 100);
        int afterSecondRound = occurrenceKeys(dense).size();
        assertTrue(afterSecondRound - afterFirst <= 100);
        plannerPort.planBinding(bindingId, NOW, 100);
        int afterThird = occurrenceKeys(dense).size();
        assertTrue(afterThird - afterSecondRound <= 100);
        assertTrue(afterThird > afterFirst);

        long sparse = createOk("reminder", Map.of(), 3, everyNDays(30, "2026-02-10", "09:00:00", allDay));
        assertEquals(0, occurrenceKeys(sparse).size());
        assertEquals(0, exhausted(sparse));
        assertTrue(nextFire(sparse) != null);
    }

    private void assertRejected(String key, Map<String, Object> body) throws Exception {
        int before = countDefinitions();
        JsonNode response = post("/api/v1/task-definitions", body);
        assertEquals("INVALID_REQUEST", response.path("code").asText(), key + " " + response);
        assertTrue(response.path("message").asText().startsWith(key), response.toString());
        assertTrue(response.path("data").isNull(), response.toString());
        assertEquals(before, countDefinitions(), key);
    }

    private void assertUnsupported(String scenario, Map<String, Object> scenarioConfig, Map<String, Object> calendar)
            throws Exception {
        int before = countDefinitions();
        Map<String, Object> config = new LinkedHashMap<>(calendar);
        config.remove("timeWindow");
        JsonNode response = post("/api/v1/task-definitions", body(scenario, scenarioConfig, 3, config));
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", response.path("code").asText(), scenario + " " + response);
        assertEquals(before, countDefinitions(), scenario);
    }

    private long createOk(String scenario, Map<String, Object> scenarioConfig, int schema, Map<String, Object> calendar)
            throws Exception {
        JsonNode created = post("/api/v1/task-definitions", body(scenario, scenarioConfig, schema, calendar));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private Map<String, Object> dailyBody(int schema, Map<String, Object> calendar) {
        return body("reminder", Map.of(), schema, calendar);
    }

    private Map<String, Object> body(
            String scenario, Map<String, Object> scenarioConfig, int schema, Map<String, Object> calendar) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", scenario);
        body.put("scenarioSchemaVersion", 1);
        body.put("title", "P14");
        body.put("description", "");
        body.put("scenarioConfig", scenarioConfig);
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", schema,
                        "config", calendar)));
        return body;
    }

    private Map<String, Object> command(
            long revision, String scenario, String title, int schema, Map<String, Object> calendar) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioSchemaVersion", 1);
        payload.put("title", title);
        payload.put("description", "");
        payload.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        payload.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", schema,
                        "config", calendar)));
        payload.put("scenarioConfig", "reminder".equals(scenario) ? Map.of() : Map.of());
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", revision,
                "commandSchemaVersion", 1,
                "payload", payload);
    }

    private Map<String, Object> daily(String localTime) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "DAILY");
        config.put("startDate", "2026-01-01");
        config.put("localTime", localTime);
        config.put("zoneId", "Asia/Shanghai");
        return config;
    }

    private Map<String, Object> daily(String localTime, Object timeWindow) {
        Map<String, Object> config = daily(localTime);
        config.put("timeWindow", timeWindow);
        return config;
    }

    private Map<String, Object> once(String localDate, String localTime, Object timeWindow) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "ONCE");
        config.put("localDate", localDate);
        config.put("localTime", localTime);
        config.put("zoneId", "Asia/Shanghai");
        config.put("timeWindow", timeWindow);
        return config;
    }

    private Map<String, Object> minutes(int interval, Object timeWindow) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "EVERY_N_MINUTES");
        config.put("startDate", "2026-01-05");
        config.put("intervalMinutes", interval);
        config.put("localTime", "00:00:00");
        config.put("zoneId", "Asia/Shanghai");
        config.put("timeWindow", timeWindow);
        return config;
    }

    private Map<String, Object> everyNDays(int interval, String startDate, String localTime, Object timeWindow) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "EVERY_N_DAYS");
        config.put("startDate", startDate);
        config.put("intervalDays", interval);
        config.put("localTime", localTime);
        config.put("zoneId", "Asia/Shanghai");
        config.put("timeWindow", timeWindow);
        return config;
    }

    private Map<String, Object> window(String mode, List<Map<String, String>> workday, List<Map<String, String>> nonWorkday) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mode", mode);
        value.put("workdaySegments", workday);
        value.put("nonWorkdaySegments", nonWorkday);
        return value;
    }

    private Map<String, String> seg(String start, String end) {
        Map<String, String> segment = new LinkedHashMap<>();
        segment.put("start", start);
        segment.put("end", end);
        return segment;
    }

    private Map<String, Object> anniversaryConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("kind", "BIRTHDAY");
        config.put("subjectName", "林安");
        config.put("month", 6);
        config.put("dayOfMonth", 1);
        config.put("localTime", "09:00:00");
        config.put("startYear", 2000);
        return config;
    }

    private Map<String, Object> deadlineConfig() {
        return Map.of(
                "subjectType", "CONTRACT",
                "dueDate", "2026-06-01",
                "localTime", "09:00:00",
                "offsetDays", List.of(1, 0));
    }

    private Map<String, Object> paymentConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("amountFen", 10000);
        config.put("periodLabel", "2026房租");
        config.put("dueDate", "2026-06-01");
        config.put("localTime", "09:00:00");
        config.put("offsetDays", List.of(1, 0));
        return config;
    }

    private Map<String, Object> maintenanceConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("subjectName", "滤芯");
        config.put("intervalDays", 30);
        config.put("nextDueDate", LocalDate.parse("2026-06-01").toString());
        config.put("localTime", "09:00:00");
        return config;
    }

    private List<String> occurrenceKeys(long definitionId) {
        return jdbc.queryForList(
                """
                SELECT occurrence_key FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING'
                ORDER BY occurrence_at
                """,
                String.class,
                definitionId);
    }

    private int signalCount(long definitionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_signal WHERE definition_id = ? AND process_status <> 'IGNORED'",
                Integer.class,
                definitionId);
        return count == null ? 0 : count;
    }

    private int schemaVersion(long definitionId) {
        Integer version = jdbc.queryForObject(
                "SELECT schema_version FROM tt_trigger_binding WHERE definition_id = ?", Integer.class, definitionId);
        return version == null ? 0 : version;
    }

    private long scheduleGeneration(long definitionId) {
        Long generation = jdbc.queryForObject(
                "SELECT schedule_generation FROM tt_trigger_binding WHERE definition_id = ?", Long.class, definitionId);
        return generation == null ? 0 : generation;
    }

    private int exhausted(long definitionId) {
        Integer value = jdbc.queryForObject(
                "SELECT exhausted FROM tt_trigger_binding WHERE definition_id = ?", Integer.class, definitionId);
        return value == null ? -1 : value;
    }

    private Object nextFire(long definitionId) {
        return jdbc.queryForObject(
                "SELECT next_fire_at FROM tt_trigger_binding WHERE definition_id = ?", Object.class, definitionId);
    }

    private long bindingId(long definitionId) {
        Long id = jdbc.queryForObject(
                "SELECT trigger_binding_id FROM tt_trigger_binding WHERE definition_id = ?", Long.class, definitionId);
        return id == null ? 0 : id;
    }

    private long revision(long definitionId) {
        Long revision = jdbc.queryForObject(
                "SELECT revision FROM tt_task_definition WHERE definition_id = ?", Long.class, definitionId);
        return revision == null ? 0 : revision;
    }

    private int countDefinitions() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition", Integer.class);
        return count == null ? 0 : count;
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
