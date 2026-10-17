package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** P09 三个到期类场景的真库纵向路径。 */
@SpringBootTest(classes = TimeImprintTaskApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class P09DeadlineMysqlIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = new ItHttpFixture();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://127.0.0.1:13306/db_timeimprint_task?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @Test
    void anniversaryEditKeepsPreviousMonthDay() throws Exception {
        LocalDate day = LocalDate.now(ZONE).plusDays(2);
        long definitionId = create("anniversary", "林安", anniversaryConfig(day), yearlyBinding(day));
        assertEquals(1, count("tt_anniversary_subject", definitionId));

        LocalDate edited = day.plusDays(1);
        JsonNode updated = post("/api/v1/task-definitions/" + definitionId + "/commands/update",
                command(definitionRevision(definitionId), updatePayload("林安", anniversaryConfig(edited), yearlyBinding(edited))));
        assertEquals("OK", updated.path("code").asText(), updated.toString());
        Integer revisions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_anniversary_revision WHERE definition_id=?", Integer.class, definitionId);
        assertEquals(1, revisions);
    }

    @Test
    void deadlineRenewalAndCancel() throws Exception {
        LocalDate due = LocalDate.now(ZONE).plusDays(3);
        long definitionId = create("deadline", "合同", deadlineConfig(due), anchorBinding(due, false));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM tt_deadline_subject WHERE definition_id=?", String.class, definitionId));

        LocalDate renewed = due.plusDays(1);
        JsonNode updated = post("/api/v1/task-definitions/" + definitionId + "/commands/update",
                command(definitionRevision(definitionId), updatePayload("合同", deadlineConfig(renewed), anchorBinding(renewed, false))));
        assertEquals("OK", updated.path("code").asText(), updated.toString());
        assertEquals(1, count("tt_deadline_renewal", definitionId));

        long instanceId = firstInstance(definitionId);
        JsonNode cancelled = post("/api/v1/task-instances/" + instanceId + "/commands/cancel",
                command(instanceRevision(instanceId), Map.of()));
        assertEquals("OK", cancelled.path("code").asText(), cancelled.toString());
        assertEquals("CANCELLED", jdbc.queryForObject(
                "SELECT status FROM tt_deadline_subject WHERE definition_id=?", String.class, definitionId));
    }

    @Test
    void paymentPartialThenPaidThenReverse() throws Exception {
        LocalDate due = LocalDate.now(ZONE).plusDays(3);
        long definitionId = create("payment", "房租", paymentConfig(due), anchorBinding(due, true));
        long instanceId = firstInstance(definitionId);
        JsonNode partial = post("/api/v1/task-instances/" + instanceId + "/commands/confirmPayment",
                command(instanceRevision(instanceId), Map.of("amountFen", 4000)));
        assertEquals("OK", partial.path("code").asText(), partial.toString());
        assertEquals("PARTIAL", status(definitionId));
        assertEquals(4000L, paid(definitionId));

        JsonNode paidOff = post("/api/v1/task-instances/" + instanceId + "/commands/confirmPayment",
                command(instanceRevision(instanceId), Map.of("amountFen", 6000)));
        assertEquals("OK", paidOff.path("code").asText(), paidOff.toString());
        assertEquals("PAID", status(definitionId));
        assertEquals("RETIRED", controlState(definitionId));

        JsonNode reversed = post("/api/v1/task-instances/" + instanceId + "/commands/reverse",
                command(instanceRevision(instanceId), Map.of()));
        assertEquals("OK", reversed.path("code").asText(), reversed.toString());
        assertEquals(0L, paid(definitionId));
        assertTrue(List.of("UNPAID", "OVERDUE").contains(status(definitionId)), status(definitionId));
        assertEquals("ACTIVE", controlState(definitionId));
    }

    private long create(String scenario, String title, Map<String, Object> config, Map<String, Object> binding)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", scenario);
        body.put("scenarioSchemaVersion", 1);
        body.put("title", title);
        body.put("description", "");
        body.put("scenarioConfig", config);
        body.put("participants", List.of(Map.of(
                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put("triggerBindings", List.of(binding));
        JsonNode created = post("/api/v1/task-definitions", body);
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private Map<String, Object> anniversaryConfig(LocalDate day) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("kind", "BIRTHDAY");
        config.put("subjectName", "林安");
        config.put("month", day.getMonthValue());
        config.put("dayOfMonth", day.getDayOfMonth());
        config.put("localTime", "09:00:00");
        config.put("startYear", 2000);
        return config;
    }

    private Map<String, Object> deadlineConfig(LocalDate due) {
        return Map.of(
                "subjectType", "CONTRACT",
                "dueDate", due.toString(),
                "localTime", "09:00:00",
                "offsetDays", List.of(1, 0));
    }

    private Map<String, Object> paymentConfig(LocalDate due) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("amountFen", 10000);
        config.put("periodLabel", "2026房租");
        config.put("dueDate", due.toString());
        config.put("localTime", "09:00:00");
        config.put("offsetDays", List.of(1, 0));
        return config;
    }

    private Map<String, Object> yearlyBinding(LocalDate day) {
        return Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 2,
                "config", Map.of(
                        "type", "YEARLY",
                        "startDate", day.toString(),
                        "month", day.getMonthValue(),
                        "dayOfMonth", day.getDayOfMonth(),
                        "offsetDays", List.of(-1, 0),
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));
    }

    private Map<String, Object> anchorBinding(LocalDate due, boolean dailyAfter) {
        return Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 2,
                "config", Map.of(
                        "type", "ANCHOR_OFFSETS",
                        "anchorDate", due.toString(),
                        "offsetDays", List.of(-1, 0),
                        "dailyAfterAnchor", dailyAfter,
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"));
    }

    private Map<String, Object> updatePayload(String title, Map<String, Object> config, Map<String, Object> binding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioSchemaVersion", 1);
        payload.put("title", title);
        payload.put("description", "");
        payload.put("participants", List.of(Map.of(
                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        payload.put("triggerBindings", List.of(binding));
        payload.put("scenarioConfig", config);
        return payload;
    }

    private Map<String, Object> command(long revision, Map<String, Object> payload) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", revision,
                "commandSchemaVersion", 1,
                "payload", payload);
    }

    private long definitionRevision(long definitionId) throws Exception {
        JsonNode body = get("/api/v1/task-definitions/" + definitionId);
        return body.path("data").path("revision").asLong();
    }

    private long instanceRevision(long instanceId) throws Exception {
        return get("/api/v1/task-instances/" + instanceId).path("data").path("revision").asLong();
    }

    private long firstInstance(long definitionId) {
        Long id = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id=? AND scenario_state='PLANNED' ORDER BY instance_id LIMIT 1",
                Long.class, definitionId);
        assertTrue(id != null && id > 0);
        return id;
    }

    private int count(String table, long definitionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE definition_id=?", Integer.class, definitionId);
        return n == null ? 0 : n;
    }

    private String status(long definitionId) {
        return jdbc.queryForObject("SELECT status FROM tt_payment_subject WHERE definition_id=?", String.class, definitionId);
    }

    private long paid(long definitionId) {
        return jdbc.queryForObject("SELECT paid_fen FROM tt_payment_subject WHERE definition_id=?", Long.class, definitionId);
    }

    private String controlState(long definitionId) {
        return jdbc.queryForObject(
                "SELECT control_state FROM tt_task_definition WHERE definition_id=?", String.class, definitionId);
    }

    private JsonNode post(String path, Object body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
