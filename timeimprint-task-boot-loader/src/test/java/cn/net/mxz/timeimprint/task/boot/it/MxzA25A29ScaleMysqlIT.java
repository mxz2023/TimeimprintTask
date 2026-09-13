package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.limit.MxzCreateWriteLimitsValidator;
import cn.net.mxz.timeimprint.task.service.application.limit.MxzPlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
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

/** A25/A29 scale limits at create time (participants, JSON bytes). */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA25A29ScaleMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

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
    void a25RejectParticipantListOver50BeforeUniqueness() throws Exception {
        List<Map<String, Object>> participants = new ArrayList<>();
        for (int i = 0; i < MxzPlatformLimits.MAX_PARTICIPANTS_PER_SCOPE + 1; i++) {
            participants.add(Map.of(
                    "principalType", "USER",
                    "principalId", "local-actor",
                    "roleCode", "OWNER"));
        }
        JsonNode resp = postCreate("A25 over participants", participants, "ok", Map.of());
        assertEquals("INVALID_REQUEST", resp.path("code").asText(), resp.toString());
        assertTrue(resp.path("message").asText().toLowerCase().contains("participant"), resp.toString());
        Integer defs = jdbc.queryForObject("SELECT COUNT(*) FROM tt_task_definition WHERE title = ?", Integer.class, "A25 over participants");
        assertEquals(0, defs == null ? -1 : defs);
    }

    @Test
    void a29RejectOversizedScenarioConfigJson() {
        String huge = "x".repeat(MxzPlatformLimits.MAX_JSON_VALUE_BYTES + 1);
        var participants = List.of(new MxzCreateDefinitionCommand.ParticipantInput("USER", "local-actor", "OWNER"));
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException.class,
                () -> MxzCreateWriteLimitsValidator.validateBeforeWrite(
                        "ok",
                        "{\"pad\":\"" + huge + "\"}",
                        participants,
                        List.of(),
                        now,
                        now.plusSeconds(86400),
                        objectMapper));
        assertEquals("INVALID_REQUEST", ex.errorCode());
        assertTrue(ex.getMessage().toLowerCase().contains("large")
                || ex.getMessage().toLowerCase().contains("scenarioconfig"));
    }

    private JsonNode postCreate(
            String title,
            List<Map<String, Object>> participants,
            String description,
            Map<String, Object> scenarioConfig)
            throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusHours(2).withNano(0);
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
        Map<String, Object> body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", description,
                "scenarioConfig", scenarioConfig,
                "participants", participants,
                "triggerBindings", List.of(trigger));
        return post("/api/v1/task-definitions", body);
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
