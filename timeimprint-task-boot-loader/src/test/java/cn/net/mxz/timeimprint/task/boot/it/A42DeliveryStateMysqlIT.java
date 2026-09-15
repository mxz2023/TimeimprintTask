package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.ActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

/** A42: deliveryState DELIVERED after S01 vertical success. */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A42DeliveryStateMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

    @Autowired
    ActionWorker actionWorker;

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
    void a42S01SuccessYieldsDelivered() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(2).withNano(0);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A42 delivery",
                        "description", "",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText());
        long definitionId = created.path("data").path("definitionId").asLong();
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ?", Long.class, definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ?", Long.class, instanceId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE instance_id = ? LIMIT 1",
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

        JsonNode inst = get("/api/v1/task-instances/" + instanceId);
        assertEquals("DELIVERED", inst.path("data").path("deliverySummary").path("deliveryState").asText());
        assertEquals(1, inst.path("data").path("deliverySummary").path("succeededCount").asInt());
        assertEquals(1, inst.path("data").path("deliverySummary").path("totalCount").asInt());
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
