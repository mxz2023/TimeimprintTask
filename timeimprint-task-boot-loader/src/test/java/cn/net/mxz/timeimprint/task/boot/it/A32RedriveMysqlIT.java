package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.domain.request.RedriveRequest;
import cn.net.mxz.timeimprint.task.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
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

/** A32 / I06-I07: DEAD LOCAL Action redrive chain, idempotency, EXTERNAL rejected, max 3. */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class A32RedriveMysqlIT {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TaskGateway gateway;

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
    void a32LocalActionRedriveChainAndIdempotency() throws Exception {
        long deadActionId = prepareDeadLocalAction();
        int attemptsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_attempt WHERE action_job_id = ?", Integer.class, deadActionId);

        String requestId = UUID.randomUUID().toString();
        var req = new RedriveRequest(requestId, "DEAD", "A32 IT redrive");
        var view1 = gateway.redriveAction(deadActionId, req);
        long newId1 = Long.parseLong(view1.actionJobId());
        assertNotEquals(deadActionId, newId1);
        assertEquals("READY", view1.storedStatus());
        assertEquals(1, view1.redriveNo());
        Long parent1 = jdbc.queryForObject(
                "SELECT parent_action_job_id FROM tt_action_job WHERE action_job_id = ?", Long.class, newId1);
        assertEquals(deadActionId, parent1);

        var view2 = gateway.redriveAction(deadActionId, req);
        assertEquals(newId1, Long.parseLong(view2.actionJobId()), "same requestId replays redrive result");

        int attemptsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_action_attempt WHERE action_job_id = ?", Integer.class, deadActionId);
        assertEquals(attemptsBefore, attemptsAfter, "DEAD source attempt history unchanged");
    }

    @Test
    void a32RejectExternalRedrive() throws Exception {
        Long externalId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE execution_mode = 'EXTERNAL' LIMIT 1
                """,
                Long.class);
        if (externalId == null) {
            return;
        }
        jdbc.update(
                "UPDATE tt_action_job SET status = 'DEAD', outcome_code = 'TEST' WHERE action_job_id = ?",
                externalId);
        var req = new RedriveRequest(UUID.randomUUID().toString(), "DEAD", "external");
        ApplicationException ex =
                assertThrows(ApplicationException.class, () -> gateway.redriveAction(externalId, req));
        assertEquals("STATE_CONFLICT", ex.errorCode());
    }

    @Test
    void a32MaxThreeRedrives() throws Exception {
        long deadActionId = prepareDeadLocalAction();
        for (int i = 0; i < 3; i++) {
            gateway.redriveAction(
                    deadActionId, new RedriveRequest(UUID.randomUUID().toString(), "DEAD", "r" + i));
        }
        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> gateway.redriveAction(
                        deadActionId, new RedriveRequest(UUID.randomUUID().toString(), "DEAD", "fourth")));
        assertEquals("STATE_CONFLICT", ex.errorCode());
        assertTrue(ex.getMessage().contains("max redrive"));
    }

    @Test
    void i06SignalRedriveFromDead() throws Exception {
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal ORDER BY signal_id DESC LIMIT 1", Long.class);
        jdbc.update(
                """
                UPDATE tt_task_signal SET
                  process_status = 'DEAD',
                  result_code = 'TEST',
                  result_summary = 'I06 IT',
                  processed_at = UTC_TIMESTAMP(),
                  attempt_count = max_attempts,
                  lease_owner = NULL,
                  lease_until = NULL,
                  execution_token = NULL
                WHERE signal_id = ?
                """,
                signalId);
        String requestId = UUID.randomUUID().toString();
        var view = gateway.redriveSignal(signalId, new RedriveRequest(requestId, "DEAD", "I06 IT"));
        assertEquals("READY", view.processStatus());
        assertEquals(1, view.redriveNo());
        var replay = gateway.redriveSignal(signalId, new RedriveRequest(requestId, "DEAD", "I06 IT"));
        assertEquals(view.signalId(), replay.signalId());
    }

    private long prepareDeadLocalAction() throws Exception {
        long definitionId = createMinimalS02();
        Long instanceId = jdbc.queryForObject(
                "SELECT instance_id FROM tt_task_instance WHERE definition_id = ? ORDER BY occurrence_at LIMIT 1",
                Long.class,
                definitionId);
        Long signalId = jdbc.queryForObject(
                "SELECT signal_id FROM tt_task_signal WHERE instance_id = ? LIMIT 1", Long.class, instanceId);
        gateway.processSignal(signalId);
        Long actionJobId = jdbc.queryForObject(
                """
                SELECT action_job_id FROM tt_action_job
                WHERE instance_id = ? AND execution_mode = 'LOCAL_TRANSACTIONAL' AND status = 'READY'
                ORDER BY action_job_id DESC LIMIT 1
                """,
                Long.class,
                instanceId);
        jdbc.update(
                """
                UPDATE tt_action_job SET status = 'DEAD', outcome_code = 'A32_TEST', completed_at = UTC_TIMESTAMP()
                WHERE action_job_id = ?
                """,
                actionJobId);
        return actionJobId;
    }

    private long createMinimalS02() throws Exception {
        var zone = java.time.ZoneId.of("Asia/Shanghai");
        var occurrence = java.time.ZonedDateTime.now(zone).minusMinutes(1).withNano(0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Map<String, Object> daily = Map.of(
                "type", "DAILY",
                "startDate", occurrence.toLocalDate().toString(),
                "localTime", fmt.format(occurrence.toLocalTime()),
                "zoneId", "Asia/Shanghai");
        var body = Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "recurring_todo",
                "scenarioSchemaVersion", 1,
                "title", "A32 redrive",
                "description", "",
                "scenarioConfig", Map.of(),
                "participants",
                java.util.List.of(
                        Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                java.util.List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", daily)));
        var http = java.net.http.HttpClient.newHttpClient();
        String json = objectMapper.writeValueAsString(body);
        var req = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/task-definitions"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
        var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        var node = objectMapper.readTree(resp.body());
        assertEquals("OK", node.path("code").asText(), node.toString());
        return node.path("data").path("definitionId").asLong();
    }
}
