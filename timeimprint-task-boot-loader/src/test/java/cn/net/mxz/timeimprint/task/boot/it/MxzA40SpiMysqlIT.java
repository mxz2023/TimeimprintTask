package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.service.MxzDefinitionCommandService;
import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

/**
 * A40 (minimal): SPI catalog (E01), unknown instance command rejected; definition {@code pause} is handled by
 * {@link MxzDefinitionCommandService}, not {@link TaskCommandHandler}.
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA40SpiMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ExtensionRegistry extensionRegistry;

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
    void e01ListsRegisteredScenarios() throws Exception {
        JsonNode resp = get("/api/v1/task-scenarios");
        assertEquals("OK", resp.path("code").asText(), resp.toString());
        assertTrue(resp.path("data").path("items").isArray());
        assertFalse(resp.path("data").path("items").isEmpty());
    }

    @Test
    void unknownInstanceCommandRejected() throws Exception {
        long instanceId = createOnceInstanceId();
        long revision = jdbc.queryForObject(
                "SELECT revision FROM tt_task_instance WHERE instance_id = ?", Long.class, instanceId);
        JsonNode resp = post(
                "/api/v1/task-instances/" + instanceId + "/commands/not-a-real-command",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "expectedRevision", revision,
                        "commandSchemaVersion", 1,
                        "payload", Map.of()));
        assertEquals("COMMAND_NOT_SUPPORTED", resp.path("code").asText(), resp.toString());
    }

    @Test
    void definitionPauseNotRoutedThroughTaskCommandHandler() {
        assertFalse(extensionRegistry
                .commandHandlers()
                .find(new TaskCommandHandlerKey("reminder", CommandScope.INSTANCE, "pause", 1))
                .isPresent());
        assertTrue(MxzDefinitionCommandService.class.getSimpleName().startsWith("Mxz"));
    }

    private long createOnceInstanceId() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(2).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A40 " + UUID.randomUUID(),
                        "description", "",
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
                                        "type", "ONCE",
                                        "localDate", occurrence.toLocalDate().toString(),
                                        "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        return jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
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
