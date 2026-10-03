package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.HttpClient;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A06：complete 与站内通知 Action 并发；终态唯一，已成功收件保留，其余 READY/RETRY_WAIT 取消。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A06CompleteNotificationMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

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
    void a06CompleteRacesInitialNotificationAction() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        LocalDate date = occurrence.toLocalDate();
        LocalTime time = occurrence.toLocalTime().withNano(0);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", date.toString(),
                        "localTime", TIME_FMT.format(time),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", "A06 race",
                        "description", "complete vs notification",
                        "scenarioConfig",
                        Map.of(
                                "chaseOffsetsMinutes", List.of(60, 240),
                                "notificationExpireAfterMinutes", 1440,
                                "maxSnoozeCount", 3),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                instanceId);
        gateway.processSignal(signalId);

        JsonNode pending = get("/api/v1/task-instances/" + instanceId);
        assertEquals("PENDING", pending.path("data").path("scenarioState").asText(), pending.toString());
        long revision = pending.path("data").path("revision").asLong();

        Integer readyCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_action_job
                WHERE instance_id = ? AND status IN ('READY', 'RETRY_WAIT')
                """,
                Integer.class,
                instanceId);
        assertTrue(readyCount >= 2, "expect INITIAL + at least one CHASE READY");

        Long initialActionId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND action_key LIKE 'INITIAL:%'
                LIMIT 1
                """,
                Long.class,
                instanceId);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND
                WHERE action_job_id = ?
                """,
                initialActionId);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> completeCall = () -> {
                start.await(5, TimeUnit.SECONDS);
                return post(
                        "/api/v1/task-instances/" + instanceId + "/commands/complete",
                        Map.of(
                                "requestId", UUID.randomUUID().toString(),
                                "expectedRevision", revision,
                                "commandSchemaVersion", 1,
                                "payload", Map.of()));
            };
            Callable<Void> actionCall = () -> {
                start.await(5, TimeUnit.SECONDS);
                actionWorker.executeAction(initialActionId);
                return null;
            };
            Future<JsonNode> completeFuture = pool.submit(completeCall);
            Future<Void> actionFuture = pool.submit(actionCall);
            start.countDown();
            JsonNode completeResult = completeFuture.get(30, TimeUnit.SECONDS);
            actionFuture.get(30, TimeUnit.SECONDS);
            assertEquals("OK", completeResult.path("code").asText(), completeResult.toString());
        } finally {
            pool.shutdownNow();
        }

        JsonNode terminal = get("/api/v1/task-instances/" + instanceId);
        assertEquals("TERMINAL", terminal.path("data").path("lifecycleCategory").asText(), terminal.toString());
        String scenarioState = terminal.path("data").path("scenarioState").asText();
        assertTrue(
                "COMPLETED".equals(scenarioState) || "SKIPPED".equals(scenarioState),
                "unexpected terminal scenarioState: " + scenarioState);

        Integer inbox = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE instance_id = ?",
                Integer.class,
                instanceId);
        assertEquals(1, inbox, "one succeeded notification inbox must remain");

        Integer openActions = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_action_job
                WHERE instance_id = ? AND status IN ('READY', 'RETRY_WAIT')
                """,
                Integer.class,
                instanceId);
        assertEquals(0, openActions, "terminal must cancel unstarted actions");

        Integer succeeded = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_action_job
                WHERE instance_id = ? AND status = 'SUCCEEDED'
                """,
                Integer.class,
                instanceId);
        assertTrue(succeeded <= 1, "at most one action may succeed (INITIAL if won race)");
    }

    @Test
    void completeAndDueSignalDoNotLockWait() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> trigger = Map.of(
                "bindingKey", "primary",
                "providerKey", "calendar",
                "schemaVersion", 1,
                "config",
                Map.of(
                        "type", "DAILY",
                        "startDate", occurrence.toLocalDate().toString(),
                        "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                        "zoneId", "Asia/Shanghai"));
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", "A06 lock order",
                        "description", "signal and complete",
                        "scenarioConfig",
                        Map.of(
                                "chaseOffsetsMinutes", List.of(60),
                                "notificationExpireAfterMinutes", 1440,
                                "maxSnoozeCount", 1),
                        "participants",
                        List.of(Map.of(
                                "principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                        "triggerBindings", List.of(trigger)));
        assertEquals("OK", created.path("code").asText(), created.toString());
        long definitionId = Long.parseLong(created.path("data").path("definitionId").asText());
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE definition_id = ? AND instance_id = ? LIMIT 1",
                Long.class,
                definitionId,
                instanceId);
        JsonNode before = get("/api/v1/task-instances/" + instanceId);
        long revision = before.path("data").path("revision").asLong();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> signalFuture = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                gateway.processSignal(signalId);
                return null;
            });
            Future<JsonNode> completeFuture = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return post(
                        "/api/v1/task-instances/" + instanceId + "/commands/complete",
                        Map.of(
                                "requestId", UUID.randomUUID().toString(),
                                "expectedRevision", revision,
                                "commandSchemaVersion", 1,
                                "payload", Map.of()));
            });
            start.countDown();
            signalFuture.get(40, TimeUnit.SECONDS);
            JsonNode completeResult = completeFuture.get(40, TimeUnit.SECONDS);
            String code = completeResult.path("code").asText();
            assertTrue(
                    "OK".equals(code) || "STATE_CONFLICT".equals(code) || "REVISION_CONFLICT".equals(code),
                    completeResult.toString());
            assertTrue(!completeResult.toString().contains("Lock wait timeout"), completeResult.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url(path))).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
