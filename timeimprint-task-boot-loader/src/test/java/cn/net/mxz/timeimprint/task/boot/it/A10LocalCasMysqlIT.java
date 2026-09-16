package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.runtime.action.worker.ActionWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A10：LOCAL_TRANSACTIONAL 效果写入后 CAS 失败 → 整笔回滚，可安全重试，最终一条 inbox。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A10LocalCasMysqlIT {

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
    void a10CasFailureRollsBackEffectThenRetryYieldsOneInbox() throws Exception {
        long actionJobId = prepareDueInAppAction("A10 local CAS");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> actionWorker.executeLocalWithForcedCasFailure(actionJobId));
        assertTrue(ex.getMessage().contains("CAS_FAILED"), ex.getMessage());

        assertEquals("READY", statusOf(actionJobId), "claim/effect/attempt must roll back");
        assertEquals(0, inboxCount(actionJobId));
        assertEquals(0, attemptCount(actionJobId));

        actionWorker.executeAction(actionJobId);
        assertEquals("SUCCEEDED", statusOf(actionJobId));
        assertEquals(1, inboxCount(actionJobId), "exactly one inbox after safe retry");
        assertEquals(1, attemptCount(actionJobId));
    }

    private long prepareDueInAppAction(String title) throws Exception {
        long definitionId = createS02(title);
        Long instanceId = jdbc.queryForObject(
                """
                SELECT instance_id FROM tt_task_instance
                WHERE definition_id = ? ORDER BY occurrence_at ASC LIMIT 1
                """,
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1",
                Long.class,
                instanceId);
        gateway.processSignal(signalId);

        // Use a still-READY LOCAL action (chase is usually not due yet after Signal).
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND status = 'READY' AND execution_mode = 'LOCAL_TRANSACTIONAL'
                ORDER BY available_at DESC LIMIT 1
                """,
                Long.class,
                instanceId);
        assertTrue(actionJobId != null && actionJobId > 0);
        jdbc.update(
                """
                UPDATE tt_action_job SET
                  available_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND,
                  next_attempt_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND
                WHERE action_job_id = ?
                """,
                actionJobId);
        jdbc.update("DELETE FROM tt_inbox WHERE action_job_id = ?", actionJobId);
        return actionJobId;
    }

    private long createS02(String title) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime", TIME_FMT.format(occurrence.toLocalTime().withNano(0)),
                "zoneId", "Asia/Shanghai");
        JsonNode created = post(
                "/api/v1/task-definitions",
                Map.of(
                        "requestId", UUID.randomUUID().toString(),
                        "scenarioKey", "recurring_todo",
                        "scenarioSchemaVersion", 1,
                        "title", title,
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
                                "config", daily))));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return Long.parseLong(created.path("data").path("definitionId").asText());
    }

    private String statusOf(long actionJobId) {
        return jdbc.queryForObject(
                "SELECT status FROM tt_action_job WHERE action_job_id = ?", String.class, actionJobId);
    }

    private int inboxCount(long actionJobId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_inbox WHERE action_job_id = ?", Integer.class, actionJobId);
        return n == null ? 0 : n;
    }

    private int attemptCount(long actionJobId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_attempt WHERE action_job_id = ?", Integer.class, actionJobId);
        return n == null ? 0 : n;
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
