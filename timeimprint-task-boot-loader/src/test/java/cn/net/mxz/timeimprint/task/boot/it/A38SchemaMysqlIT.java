package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.signal.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.signal.service.SignalProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import cn.net.mxz.timeimprint.task.boot.FlywaySchemaInformationSchemaTest;

/** A38: information_schema table set and Signal lease field lifecycle. */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A38SchemaMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    DataSource dataSource;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskSignalRepository signalRepository;

    @Autowired
    TransactionBoundary tx;

    @Autowired
    SignalProcessingService signalProcessing;

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
    void informationSchemaListsTwelvePlatformTables() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String schema = c.getCatalog();
            FlywaySchemaInformationSchemaTest.assertRequiredPlatformTablesPresent(c, schema);
        }
    }

    @Test
    @Transactional
    void signalLeaseFieldsSetWhileRunning() throws Exception {
        long signalId = createOnceSignalId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        tx.execute(() -> {
            var token = signalRepository.claimForProcessing(signalId, "a38-it", now.plusSeconds(60), now);
            assertNotNull(token.orElse(null));
            var row = signalRepository.findById(signalId).orElseThrow();
            assertEquals("RUNNING", row.processStatus());
            assertNotNull(row.leaseOwner());
            assertNotNull(row.leaseUntil());
            assertNotNull(row.executionToken());
            return null;
        });
    }

    @Test
    void signalLeaseFieldsClearedAfterSucceeded() throws Exception {
        long signalId = createOnceSignalId();
        signalProcessing.processSignal(signalId);
        assertEquals(
                "SUCCEEDED",
                jdbc.queryForObject(
                        "SELECT process_status FROM tt_task_signal WHERE signal_id = ?",
                        String.class,
                        signalId));
        assertNull(jdbc.queryForObject(
                "SELECT lease_owner FROM tt_task_signal WHERE signal_id = ?", String.class, signalId));
        assertNull(jdbc.queryForObject(
                "SELECT lease_until FROM tt_task_signal WHERE signal_id = ?", String.class, signalId));
        assertNull(jdbc.queryForObject(
                "SELECT execution_token FROM tt_task_signal WHERE signal_id = ?", String.class, signalId));
    }

    private long createOnceSignalId() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusMinutes(30).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A38 " + UUID.randomUUID(),
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
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
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
