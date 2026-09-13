package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A27: interleaved planner, signal processing, and pause without duplicate facts. */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA27InterleaveMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MxzTaskGateway gateway;

    @Autowired
    TriggerPlannerPort plannerPort;

    @Autowired
    MxzSignalProcessingService signalProcessing;

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
    void interleavedPlanProcessPause_noDuplicateOccurrences() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A27 " + UUID.randomUUID(),
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
        long revision = created.path("data").path("revision").asLong();
        Long bindingId = jdbc.queryForObject(
                "SELECT trigger_binding_id FROM tt_trigger_binding WHERE definition_id = ?",
                Long.class,
                definitionId);
        assertNotNull(bindingId);

        wipeRuntimeRows(definitionId);
        jdbc.update(
                """
                UPDATE tt_trigger_binding SET
                  exhausted = 0,
                  next_fire_at = UTC_TIMESTAMP() - INTERVAL 1 DAY,
                  cursor_json = CAST('{}' AS JSON),
                  revision = revision + 1,
                  updated_at = UTC_TIMESTAMP()
                WHERE trigger_binding_id = ?
                """,
                bindingId);

        CountDownLatch start = new CountDownLatch(1);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Callable<Void> plan = () -> {
            start.await(5, TimeUnit.SECONDS);
            plannerPort.planBinding(bindingId, now, 10);
            return null;
        };
        Callable<Void> pause = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                post(
                        "/api/v1/task-definitions/" + definitionId + "/commands/pause",
                        Map.of(
                                "requestId", UUID.randomUUID().toString(),
                                "expectedRevision", revision,
                                "commandSchemaVersion", 1,
                                "payload", Map.of()));
            } catch (Exception ignored) {
                // concurrent revision drift is acceptable; invariants checked after join
            }
            return null;
        };
        Callable<Void> process = () -> {
            start.await(5, TimeUnit.SECONDS);
            List<Long> ids = jdbc.query(
                    "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? ORDER BY signal_id DESC LIMIT 1",
                    (rs, rowNum) -> rs.getLong(1),
                    definitionId);
            if (!ids.isEmpty()) {
                signalProcessing.processSignal(ids.get(0));
            }
            return null;
        };
        Future<Void> f1 = pool.submit(plan);
        Future<Void> f2 = pool.submit(plan);
        Future<Void> f3 = pool.submit(process);
        Future<Void> f4 = pool.submit(pause);
        start.countDown();
        f1.get(20, TimeUnit.SECONDS);
        f2.get(20, TimeUnit.SECONDS);
        f3.get(20, TimeUnit.SECONDS);
        f4.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        Integer distinctOcc = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT occurrence_key) FROM tt_task_instance WHERE definition_id = ?",
                Integer.class,
                definitionId);
        assertTrue(distinctOcc == null || distinctOcc <= 1, "duplicate occurrence keys: " + distinctOcc);
        Integer instances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ?", Integer.class, definitionId);
        assertTrue(instances == null || instances <= 1);

        Optional<Long> signalId = jdbc.query(
                        "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                        (rs, rowNum) -> rs.getLong(1),
                        definitionId)
                .stream()
                .findFirst();
        signalId.ifPresent(signalProcessing::processSignal);
        assertEquals(
                instances == null ? 0 : instances,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ?", Integer.class, definitionId));
    }

    private void wipeRuntimeRows(long definitionId) {
        jdbc.update(
                "DELETE FROM tt_action_attempt WHERE action_job_id IN (SELECT action_job_id FROM tt_action_job WHERE definition_id = ?)",
                definitionId);
        jdbc.update("DELETE FROM tt_inbox WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_action_job WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_audit_log WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_signal WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_transition WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_instance WHERE definition_id = ?", definitionId);
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
