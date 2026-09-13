package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.MxzTaskGateway;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import cn.net.mxz.timeimprint.task.service.runtime.MxzActionWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * A03（并发规划同一 occurrence）与 A11（改时间规则与旧批次 Worker 屏障）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzA03A11MysqlIT {

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

    @Autowired
    MxzActionWorker actionWorker;

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
    void a03ConcurrentPlanSameOccurrence_singleFactsNoPrebuiltAction() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        JsonNode created = post(
                "/api/v1/task-definitions",
                createReminder(
                        "A03 plan " + UUID.randomUUID(),
                        Map.of(
                                "type", "ONCE",
                                "localDate", occurrence.toLocalDate().toString(),
                                "localTime", TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId", "Asia/Shanghai")));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long bindingId = jdbc.queryForObject(
                "SELECT trigger_binding_id FROM tt_trigger_binding WHERE definition_id = ?",
                Long.class,
                definitionId);
        assertNotNull(bindingId);

        // Wipe create-time window so two planners race to rematerialize the same occurrence.
        jdbc.update(
                "DELETE FROM tt_action_attempt WHERE action_job_id IN (SELECT action_job_id FROM tt_action_job WHERE definition_id = ?)",
                definitionId);
        jdbc.update("DELETE FROM tt_inbox WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_action_job WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_audit_log WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_signal WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_transition WHERE definition_id = ?", definitionId);
        jdbc.update("DELETE FROM tt_task_instance WHERE definition_id = ?", definitionId);
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

        LocalDateTime cursorBefore = jdbc.queryForObject(
                "SELECT next_fire_at FROM tt_trigger_binding WHERE trigger_binding_id = ?",
                LocalDateTime.class,
                bindingId);

        CountDownLatch start = new CountDownLatch(1);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Void> race = () -> {
            start.await(5, TimeUnit.SECONDS);
            plannerPort.planBinding(bindingId, now, 10);
            return null;
        };
        Future<Void> f1 = pool.submit(race);
        Future<Void> f2 = pool.submit(race);
        start.countDown();
        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        pool.shutdownNow();

        Integer instances = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_instance WHERE definition_id = ?", Integer.class, definitionId);
        Integer signals = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_task_signal WHERE definition_id = ?", Integer.class, definitionId);
        Integer actions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?", Integer.class, definitionId);
        assertEquals(1, instances);
        assertEquals(1, signals);
        assertEquals(0, actions, "Action only after Signal migration");

        LocalDateTime cursorAfter = jdbc.queryForObject(
                "SELECT next_fire_at FROM tt_trigger_binding WHERE trigger_binding_id = ?",
                LocalDateTime.class,
                bindingId);
        assertNotNull(cursorAfter);
        assertTrue(
                !cursorAfter.isBefore(cursorBefore),
                "cursor must be monotonic: before=" + cursorBefore + " after=" + cursorAfter);

        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? LIMIT 1",
                Long.class,
                definitionId);
        signalProcessing.processSignal(signalId);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tt_action_job WHERE definition_id = ?",
                        Integer.class,
                        definitionId));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT occurrence_key) FROM tt_task_instance WHERE definition_id = ?",
                        Integer.class,
                        definitionId));
    }

    @Test
    void a11CalendarUpdateBarrier_preservesHistoryCancelsFutureBlocksOldWorker() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime start = ZonedDateTime.now(zone).minusMinutes(2).withNano(0);
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", start.toLocalDate().toString(),
                "localTime", TIME_FMT.format(start.toLocalTime()),
                "zoneId", "Asia/Shanghai");
        JsonNode created = post(
                "/api/v1/task-definitions",
                createS02("A11 hist " + UUID.randomUUID(), daily));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        long scheduleGen1 = created.path("data").path("triggerBindings").get(0).path("scheduleGeneration").asLong();
        long rev = created.path("data").path("revision").asLong();

        Long pendingId = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        Long pendingSignal = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                pendingId);
        gateway.processSignal(pendingSignal);
        JsonNode pendingBefore = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingBefore.path("data").path("scenarioState").asText());
        String histTitle = pendingBefore.path("data").path("title").asText();
        String histSnapshot = jdbc.queryForObject(
                "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                String.class,
                pendingId);

        Long futureWaiting = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING'
                ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        assertNotNull(futureWaiting);
        Long oldFutureSignal = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? AND process_status = 'READY' LIMIT 1",
                Long.class,
                futureWaiting);
        assertNotNull(oldFutureSignal);

        ZonedDateTime newTime = start.plusHours(3);
        Map<String, Object> daily2 = Map.of(
                "type", "DAILY",
                "startDate", newTime.toLocalDate().toString(),
                "localTime", TIME_FMT.format(newTime.toLocalTime()),
                "zoneId", "Asia/Shanghai");

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<JsonNode> updateFut = pool.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            return post(
                    "/api/v1/task-definitions/" + definitionId + "/commands/update",
                    commandBody(
                            rev,
                            updatePayload(
                                    "A11 hist " + definitionId,
                                    "changed-desc",
                                    daily2)));
        });
        Future<Void> oldWorkerFut = pool.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            // Old batch Signal Worker — must not rematerialize cancelled future after barrier.
            try {
                gateway.processSignal(oldFutureSignal);
            } catch (RuntimeException ignored) {
                // Concurrent cancel may surface as conflict; invariants checked below.
            }
            return null;
        });
        startGate.countDown();
        JsonNode updated = updateFut.get(20, TimeUnit.SECONDS);
        oldWorkerFut.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals("OK", updated.path("code").asText(), updated.toString());

        JsonNode pendingAfter = get("/api/v1/task-instances/" + pendingId);
        assertEquals("PENDING", pendingAfter.path("data").path("scenarioState").asText());
        assertEquals(histTitle, pendingAfter.path("data").path("title").asText());
        assertEquals(
                histSnapshot,
                jdbc.queryForObject(
                        "SELECT scenario_snapshot_json FROM tt_task_instance WHERE instance_id = ?",
                        String.class,
                        pendingId));

        String oldSignalStatus = jdbc.queryForObject(
                "SELECT process_status FROM tt_task_signal WHERE signal_id = ?",
                String.class,
                oldFutureSignal);
        assertTrue(
                "IGNORED".equals(oldSignalStatus) || "SUCCEEDED".equals(oldSignalStatus),
                "old signal must be IGNORED (update first) or SUCCEEDED (signal first); was " + oldSignalStatus);
        if ("SUCCEEDED".equals(oldSignalStatus)) {
            // Signal-first: occurrence already migrated before schedule barrier; keep ACTIVE/PENDING.
            assertEquals(
                    "ACTIVE",
                    jdbc.queryForObject(
                            "SELECT lifecycle_category FROM tt_task_instance WHERE instance_id = ?",
                            String.class,
                            futureWaiting));
            assertEquals(
                    "PENDING",
                    get("/api/v1/task-instances/" + futureWaiting)
                            .path("data")
                            .path("scenarioState")
                            .asText());
        } else {
            // Update-first: old WAITING cancelled; no Action from the ignored future Signal.
            assertEquals(
                    "TERMINAL",
                    jdbc.queryForObject(
                            "SELECT lifecycle_category FROM tt_task_instance WHERE instance_id = ?",
                            String.class,
                            futureWaiting));
            assertEquals(
                    "CANCELLED",
                    jdbc.queryForObject(
                            "SELECT scenario_state FROM tt_task_instance WHERE instance_id = ?",
                            String.class,
                            futureWaiting));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM tt_action_job WHERE instance_id = ?",
                            Integer.class,
                            futureWaiting));
        }

        long scheduleGen2 = get("/api/v1/task-definitions/" + definitionId)
                .path("data")
                .path("triggerBindings")
                .get(0)
                .path("scheduleGeneration")
                .asLong();
        assertEquals(scheduleGen1 + 1, scheduleGen2);

        Integer newWaiting = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_task_instance
                WHERE definition_id = ? AND lifecycle_category = 'WAITING' AND schedule_generation = ?
                """,
                Integer.class,
                definitionId,
                scheduleGen2);
        assertTrue(newWaiting != null && newWaiting >= 1);

        // Old scheduleGeneration Signal cannot produce a second migration after barrier.
        if ("IGNORED".equals(oldSignalStatus)) {
            gateway.processSignal(oldFutureSignal);
            assertEquals(
                    "IGNORED",
                    jdbc.queryForObject(
                            "SELECT process_status FROM tt_task_signal WHERE signal_id = ?",
                            String.class,
                            oldFutureSignal));
        }

        // Update-first only: cancelled WAITING must not let leftover READY Actions succeed past barrier.
        // Signal-first ACTIVE/PENDING Actions belong to preserved history and may still complete.
        if ("IGNORED".equals(oldSignalStatus)) {
            List<Long> staleActions = jdbc.query(
                    """
                    SELECT action_job_id FROM tt_action_job
                    WHERE definition_id = ? AND status IN ('READY','RETRY_WAIT')
                      AND instance_id = ?
                    """,
                    (rs, rowNum) -> rs.getLong(1),
                    definitionId,
                    futureWaiting);
            for (Long actionId : staleActions) {
                actionWorker.executeAction(actionId);
                String st = jdbc.queryForObject(
                        "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionId);
                assertNotEquals("SUCCEEDED", st);
            }
        }
    }

    private Map<String, Object> createReminder(String title, Map<String, Object> config) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", "reminder");
        body.put("scenarioSchemaVersion", 1);
        body.put("title", title);
        body.put("description", "a03");
        body.put("scenarioConfig", Map.of());
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        return body;
    }

    private Map<String, Object> createS02(String title, Map<String, Object> config) {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("scenarioKey", "recurring_todo");
        body.put("scenarioSchemaVersion", 1);
        body.put("title", title);
        body.put("description", "a11");
        body.put("scenarioConfig", Map.of());
        body.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        body.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        return body;
    }

    private Map<String, Object> updatePayload(String title, String description, Map<String, Object> config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioSchemaVersion", 1);
        payload.put("title", title);
        payload.put("description", description);
        payload.put("scenarioConfig", Map.of());
        payload.put(
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")));
        payload.put(
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        return payload;
    }

    private Map<String, Object> commandBody(long revision, Map<String, Object> payload) {
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "expectedRevision", revision,
                "commandSchemaVersion", 1,
                "payload", payload);
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }
}
