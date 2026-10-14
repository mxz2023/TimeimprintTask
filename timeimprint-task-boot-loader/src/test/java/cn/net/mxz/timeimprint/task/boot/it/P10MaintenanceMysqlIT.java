package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
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

/** P10 保养与复查的真库纵向路径。 */
@SpringBootTest(classes = TimeImprintTaskApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class P10MaintenanceMysqlIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignalProcessingService signals;

    private final HttpClient http = new ItHttpFixture();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @Test
    void remindThenCompleteAdvancesFromTodayAndIgnoresRepeat() throws Exception {
        LocalDate due = LocalDate.now(ZONE).plusDays(2);
        long definitionId = create(due, 3);
        assertEquals(due.toString(), jdbc.queryForObject(
                "SELECT next_due_date FROM tt_maintenance_subject WHERE definition_id=?", String.class, definitionId));

        long instanceId = firstInstance(definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id=? ORDER BY signal_id LIMIT 1",
                Long.class, instanceId);
        signals.processSignal(signalId);
        JsonNode reminded = get("/api/v1/task-instances/" + instanceId);
        assertEquals("REMINDED", reminded.path("data").path("scenarioState").asText(), reminded.toString());
        assertEquals("WAITING", reminded.path("data").path("lifecycleCategory").asText(), reminded.toString());

        JsonNode completed = post("/api/v1/task-instances/" + instanceId + "/commands/complete",
                command(reminded.path("data").path("revision").asLong(), Map.of()));
        assertEquals("OK", completed.path("code").asText(), completed.toString());
        LocalDate next = LocalDate.now(ZONE).plusDays(3);
        assertEquals(next.toString(), jdbc.queryForObject(
                "SELECT next_due_date FROM tt_maintenance_subject WHERE definition_id=?", String.class, definitionId));
        assertEquals(1, count(definitionId));
        assertEquals(LocalDate.now(ZONE).toString(), jdbc.queryForObject(
                "SELECT completed_on FROM tt_maintenance_completion WHERE instance_id=?", String.class, instanceId));

        JsonNode again = post("/api/v1/task-instances/" + instanceId + "/commands/complete",
                command(instanceRevision(instanceId), Map.of()));
        assertEquals("OK", again.path("code").asText(), again.toString());
        assertEquals(1, count(definitionId));
        Integer waiting = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id=? AND lifecycle_category='WAITING'",
                Integer.class, definitionId);
        assertTrue(waiting != null && waiting >= 1, "next round must be planned");
    }

    private long create(LocalDate due, int intervalDays) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("subjectName", "滤芯");
        config.put("intervalDays", intervalDays);
        config.put("nextDueDate", due.toString());
        config.put("localTime", "09:00:00");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", "maintenance");
        body.put("scenarioSchemaVersion", 1);
        body.put("title", "换滤芯");
        body.put("description", "");
        body.put("scenarioConfig", config);
        body.put("participants", List.of(Map.of(
                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put("triggerBindings", List.of(Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 2,
                "config", Map.of(
                        "type", "ANCHOR_OFFSETS",
                        "anchorDate", due.toString(),
                        "offsetDays", List.of(0),
                        "dailyAfterAnchor", false,
                        "localTime", "09:00:00",
                        "zoneId", "Asia/Shanghai"))));
        JsonNode created = post("/api/v1/task-definitions", body);
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private Map<String, Object> command(long revision, Map<String, Object> payload) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", revision,
                "commandSchemaVersion", 1,
                "payload", payload);
    }

    private long firstInstance(long definitionId) {
        Long id = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id=? AND scenario_state='PLANNED' ORDER BY instance_id LIMIT 1",
                Long.class, definitionId);
        assertTrue(id != null && id > 0);
        return id;
    }

    private long instanceRevision(long instanceId) throws Exception {
        return get("/api/v1/task-instances/" + instanceId).path("data").path("revision").asLong();
    }

    private int count(long definitionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_maintenance_completion WHERE definition_id=?", Integer.class, definitionId);
        return n == null ? 0 : n;
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
