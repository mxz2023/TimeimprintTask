package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.service.storage.mysql.mapper.TaskSignalMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.row.TaskSignalRow;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 最小双竞争领取：两个并发 claim 不得领取同一 Signal（SKIP LOCKED）。
 */
@SpringBootTest(classes = MxzTimeImprintTaskApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("dual-process-it")
class MxzDualClaimMysqlIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () -> System.getenv()
                        .getOrDefault(
                                "DB_JDBC_URL",
                                "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"));
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_USERNAME", "tit"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_PASSWORD", "tit_local"));
        r.add("LOCAL_TENANT_ID", () -> "local-tenant");
        r.add("LOCAL_ACTOR_ID", () -> "local-actor");
        r.add("INSTANCE_ID", () -> "dual-" + UUID.randomUUID());
        r.add("WORKER_ENABLED", () -> "false");
        r.add("server.address", () -> "127.0.0.1");
    }

    @Autowired
    TaskSignalMapper signalMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentClaimsDoNotDuplicateSignal() throws Exception {
        jdbc.update(
                """
                INSERT INTO tt_task_definition(
                  tenant_id,scenario_key,scenario_schema_version,title,description,
                  scenario_config_json,scenario_config_hash,control_state,control_generation,revision,
                  created_by,updated_by,created_at,updated_at)
                VALUES('local-tenant','reminder',1,'dual',NULL,
                  CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),
                  'ACTIVE',1,1,'local-actor','local-actor',UTC_TIMESTAMP(0),UTC_TIMESTAMP(0))
                """);
        Long definitionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                """
                INSERT INTO tt_task_instance(
                  definition_id,definition_control_generation,lifecycle_category,scenario_state,
                  scenario_schema_version,scenario_snapshot_json,snapshot_hash,title_snapshot,
                  revision,created_at,updated_at)
                VALUES(?,1,'WAITING','PLANNED',1,CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),'dual',1,
                  UTC_TIMESTAMP(0),UTC_TIMESTAMP(0))
                """,
                definitionId);
        Long instanceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String signalKey = "dual-" + UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO tt_task_signal(
                  tenant_id,definition_id,instance_id,definition_control_generation,redrive_no,
                  provider_key,signal_key,schema_version,occurred_at,received_at,
                  payload_json,payload_hash,process_status,attempt_count,max_attempts,next_attempt_at,
                  created_at,updated_at)
                VALUES('local-tenant',?,?,1,0,'event',?,1,UTC_TIMESTAMP(0),UTC_TIMESTAMP(0),
                  CAST('{}' AS JSON),UNHEX(REPEAT('00',32)),'READY',0,5,UTC_TIMESTAMP(0),
                  UTC_TIMESTAMP(0),UTC_TIMESTAMP(0))
                """,
                definitionId,
                instanceId,
                signalKey);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime lease = now.plusMinutes(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<List<TaskSignalRow>> c1 = () -> signalMapper.claimReadySignals(
                now, 10, "owner-a", lease, UUID.randomUUID().toString(), now);
        Callable<List<TaskSignalRow>> c2 = () -> signalMapper.claimReadySignals(
                now, 10, "owner-b", lease, UUID.randomUUID().toString(), now);
        Future<List<TaskSignalRow>> f1 = pool.submit(c1);
        Future<List<TaskSignalRow>> f2 = pool.submit(c2);
        List<TaskSignalRow> a = f1.get();
        List<TaskSignalRow> b = f2.get();
        pool.shutdownNow();

        long targetCount = a.stream().filter(s -> signalKey.equals(s.getSignalKey())).count()
                + b.stream().filter(s -> signalKey.equals(s.getSignalKey())).count();
        assertTrue(targetCount <= 1, "same signal claimed by both workers: " + targetCount);

        // Leave no READY poison for local workers sharing this DB.
        jdbc.update(
                """
                UPDATE tt_task_signal
                SET process_status='IGNORED', result_code='TEST_CLEANUP',
                    processed_at=UTC_TIMESTAMP(0), updated_at=UTC_TIMESTAMP(0)
                WHERE signal_id=?
                """,
                jdbc.queryForObject(
                        "SELECT signal_id FROM tt_task_signal WHERE signal_key=?", Long.class, signalKey));
    }
}
