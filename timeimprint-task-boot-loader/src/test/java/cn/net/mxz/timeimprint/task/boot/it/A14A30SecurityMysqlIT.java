package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityBootstrap;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/** A14 / A30: test profile debug identity, tenant isolation, participant rules. */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("mysql-it")
class A14A30SecurityMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ActionWorker actionWorker;

    @Autowired
    SignalProcessingService signalProcessing;

    private final HttpClient http = new ItHttpFixture();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        r.add("spring.datasource.username", () -> "tit");
        r.add("spring.datasource.password", () -> "tit_local");
        r.add("timeimprint.test.tenant-id", () -> "test-tenant");
        r.add("timeimprint.test.actor-id", () -> "actor-a");
    }

    @Test
    void crossTenantDefinitionGet_returnsResourceNotFound() throws Exception {
        long otherDefId = seedOtherTenantDefinition();
        JsonNode resp = get("/api/v1/task-definitions/" + otherDefId, "test-tenant", "actor-a");
        assertEquals("RESOURCE_NOT_FOUND", resp.path("code").asText(), resp.toString());
    }

    @Test
    void nonUserPrincipalType_rejectedOnCreate() throws Exception {
        JsonNode resp = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "bad principal",
                        "description", "",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "BOT",
                                "principalId", "actor-a",
                                "roleCode", "OWNER")),
                        "triggerBindings", minimalOnceBinding()),
                "test-tenant",
                "actor-a");
        assertEquals("INVALID_REQUEST", resp.path("code").asText(), resp.toString());
    }

    @Test
    void ownerRequiredOnCreate() throws Exception {
        JsonNode resp = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "no owner",
                        "description", "",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER",
                                "principalId", "actor-b",
                                "roleCode", "RECIPIENT")),
                        "triggerBindings", minimalOnceBinding()),
                "test-tenant",
                "actor-a");
        assertEquals("INVALID_REQUEST", resp.path("code").asText(), resp.toString());
    }

    @Test
    void testProfile_allowsExplicitRecipientDistinctFromOwner() throws Exception {
        long defId = createWithRecipient("actor-a", "actor-b");
        long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                Long.class,
                defId);
        signalProcessing.processSignal(signalId);
        long actionId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job WHERE definition_id = ? LIMIT 1",
                Long.class,
                defId);
        jdbc.update(
                "UPDATE tt_action_job SET available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND, "
                        + "next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND WHERE action_job_id = ?",
                actionId);
        actionWorker.executeAction(actionId);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ? AND recipient_id = 'actor-b'",
                        Integer.class,
                        defId));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ? AND recipient_id = 'actor-a'",
                        Integer.class,
                        defId));
    }

    private long createWithRecipient(String ownerId, String recipientId) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A14 rec " + UUID.randomUUID(),
                        "description", "",
                        "scenarioConfig", Map.of(),
                        "participants",
                        List.of(
                                Map.of("principalType", "USER", "principalId", ownerId, "roleCode", "OWNER"),
                                Map.of(
                                        "principalType",
                                        "USER",
                                        "principalId",
                                        recipientId,
                                        "roleCode",
                                        "RECIPIENT")),
                        "triggerBindings", minimalOnceBinding()),
                "test-tenant",
                ownerId);
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private List<Map<String, Object>> minimalOnceBinding() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(1).withNano(0);
        return List.of(Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "ONCE",
                        "localDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                        "zoneId", "Asia/Shanghai")));
    }

    private long seedOtherTenantDefinition() {
        jdbc.update(
                """
                INSERT INTO tt_task_definition(
                  tenant_id, scenario_key, scenario_schema_version, title, description,
                  scenario_config_json, scenario_config_hash, control_state, control_generation,
                  revision, created_by, updated_by, created_at, updated_at)
                VALUES(
                  'other-tenant', 'reminder', 1, 'seed', NULL,
                  CAST('{}' AS JSON), UNHEX(SHA2('{}', 256)), 'ACTIVE', 1, 1,
                  'seed', 'seed', UTC_TIMESTAMP(), UTC_TIMESTAMP())
                """);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private static String tokenFor(String actor) {
        return switch (actor) {
            case "actor-a" -> IdentityBootstrap.ACTOR_A_TOKEN;
            case "actor-b" -> IdentityBootstrap.ACTOR_B_TOKEN;
            case "test-actor" -> IdentityBootstrap.TEST_TOKEN;
            default -> IdentityBootstrap.LOCAL_TOKEN;
        };
    }

    private JsonNode get(String path, String tenant, String actor) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenFor(actor))
                .GET()
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode post(String path, Object body, String tenant, String actor) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tokenFor(actor))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
