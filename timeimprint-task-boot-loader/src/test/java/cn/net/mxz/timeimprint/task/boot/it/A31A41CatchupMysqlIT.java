package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.application.service.SignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.ActionWorker;
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
 * A31 / A41 catch-up (minimal): stale {@code next_fire_at}, repeated planner passes, expired actions.
 * Pause/resume half of A41 is covered by {@link A12ResumeMysqlIT} (A12).
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A31A41CatchupMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TriggerPlannerPort plannerPort;

    @Autowired
    SignalProcessingService signalProcessing;

    @Autowired
    ActionWorker actionWorker;

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
    void dailyS01Catchup_plannerIdempotentThenSignal() throws Exception {
        ZonedDateTime start = ZonedDateTime.now(ZONE).minusDays(2).withNano(0);
        long definitionId = createReminderDaily(start);
        Long bindingId = jdbc.queryForObject(
                "SELECT trigger_binding_id FROM tt_trigger_binding WHERE definition_id = ?",
                Long.class,
                definitionId);
        assertNotNull(bindingId);
        jdbc.update(
                "UPDATE tt_trigger_binding SET next_fire_at = UTC_TIMESTAMP() - INTERVAL 7 DAY WHERE trigger_binding_id = ?",
                bindingId);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        plannerPort.planBinding(bindingId, now, 50);
        Integer instancesAfterFirst = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ?", Integer.class, definitionId);
        assertNotNull(instancesAfterFirst);
        assertTrue(instancesAfterFirst >= 1);
        for (int i = 0; i < 2; i++) {
            plannerPort.planBinding(bindingId, now, 50);
        }
        Integer instances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ?", Integer.class, definitionId);
        assertEquals(instancesAfterFirst, instances);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        signalProcessing.processSignal(signalId);
        // Past occurrences: INITIAL may already be EXPIRED (A31); assert no stale inbox.
        Integer inbox = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?", Integer.class, definitionId);
        assertEquals(0, inbox == null ? -1 : inbox);
        expireReadyActionsAndAssertNoInbox(definitionId);
    }

    @Test
    void dailyS02Catchup_plannerAndExpire() throws Exception {
        ZonedDateTime start = ZonedDateTime.now(ZONE).minusDays(1).withNano(0);
        long definitionId = createRecurringDaily(start);
        Long bindingId = jdbc.queryForObject(
                "SELECT trigger_binding_id FROM tt_trigger_binding WHERE definition_id = ?",
                Long.class,
                definitionId);
        jdbc.update(
                "UPDATE tt_trigger_binding SET next_fire_at = UTC_TIMESTAMP() - INTERVAL 5 DAY WHERE trigger_binding_id = ?",
                bindingId);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        plannerPort.planBinding(bindingId, now, 50);
        plannerPort.planBinding(bindingId, now, 50);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        if (signalId != null) {
            signalProcessing.processSignal(signalId);
        }
        expireReadyActionsAndAssertNoInbox(definitionId);
    }

    private void expireReadyActionsAndAssertNoInbox(long definitionId) {
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  expires_at = UTC_TIMESTAMP() - INTERVAL 1 MINUTE,
                  available_at = UTC_TIMESTAMP() - INTERVAL 1 MINUTE,
                  next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 MINUTE
                WHERE definition_id = ? AND status = 'READY'
                """,
                definitionId);
        List<Long> ids = jdbc.queryForList(
                "SELECT action_job_id FROM tt_action_job WHERE definition_id = ? AND status = 'READY'",
                Long.class,
                definitionId);
        for (Long id : ids) {
            actionWorker.executeAction(id);
        }
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_inbox WHERE definition_id = ?", Integer.class, definitionId));
        if (!ids.isEmpty()) {
            Integer expired = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ? AND status = 'EXPIRED'",
                    Integer.class,
                    definitionId);
            assertNotNull(expired);
            assertEquals(ids.size(), expired.intValue());
        }
    }

    private long createReminderDaily(ZonedDateTime start) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "reminder",
                        "scenarioSchemaVersion", 1,
                        "title", "A31 S01 " + UUID.randomUUID(),
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
                                        "type", "DAILY",
                                        "startDate", start.toLocalDate().toString(),
                                        "localTime", TIME_FMT.format(start.toLocalTime()),
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private long createRecurringDaily(ZonedDateTime start) throws Exception {
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", "A31 S02 " + UUID.randomUUID(),
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
                                        "type", "DAILY",
                                        "startDate", start.toLocalDate().toString(),
                                        "localTime", TIME_FMT.format(start.toLocalTime()),
                                        "zoneId", "Asia/Shanghai")))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
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
