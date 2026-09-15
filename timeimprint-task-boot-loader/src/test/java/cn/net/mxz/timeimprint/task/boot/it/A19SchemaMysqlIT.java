package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.FlywaySchemaInformationSchemaTest;
import cn.net.mxz.timeimprint.task.boot.TimeImprintTaskApplication;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A19 / G01：Flyway 12 表与关键 CHECK / UNIQUE 约束在真实 MySQL 上生效。
 */
@SpringBootTest(classes = TimeImprintTaskApplication.class)
@Tag("mysql-it")
class A19SchemaMysqlIT {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

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
    void a19InformationSchemaListsTwelvePlatformTables() throws Exception {
        try (var connection = dataSource.getConnection()) {
            String schema = connection.getCatalog();
            Set<String> actual = FlywaySchemaInformationSchemaTest.queryTableNames(connection, schema);
            for (String expected : FlywaySchemaInformationSchemaTest.expectedTableNames()) {
                assertTrue(actual.contains(expected), "missing table: " + expected);
            }
            assertTrue(
                    actual.size() >= FlywaySchemaInformationSchemaTest.expectedTableNames().size(),
                    "expected at least 12 tt_ platform tables, got " + actual);
        }
    }

    @Test
    void a19ActionAttemptBoundsCheckRejectsOverMax() {
        Long actionJobId = jdbc.queryForObject(
                "SELECT action_job_id FROM tt_action_job ORDER BY action_job_id DESC LIMIT 1",
                Long.class);
        if (actionJobId == null) {
            return;
        }
        DataAccessException ex = assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        """
                        UPDATE tt_action_job SET attempt_count = max_attempts + 1
                        WHERE action_job_id = ?
                        """,
                        actionJobId));
        assertConstraintViolation(ex, "chk_action_attempt_bounds");
    }

    @Test
    void a19SignalSourceUniqueRejectsDuplicateKey() {
        var row = jdbc.queryForMap(
                """
                SELECT tenant_id, definition_id, trigger_binding_id, instance_id,
                       definition_control_generation, provider_key, signal_key, schema_version,
                       occurred_at, received_at, payload_json, payload_hash,
                       process_status, attempt_count, max_attempts, next_attempt_at,
                       processed_at, result_code, result_summary
                FROM tt_task_signal
                ORDER BY signal_id DESC
                LIMIT 1
                """);
        DataAccessException ex = assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        """
                        INSERT INTO tt_task_signal (
                            tenant_id, definition_id, trigger_binding_id, instance_id,
                            definition_control_generation, provider_key, signal_key, schema_version,
                            occurred_at, received_at, payload_json, payload_hash,
                            process_status, attempt_count, max_attempts, next_attempt_at,
                            processed_at, result_code, result_summary,
                            created_at, updated_at
                        ) VALUES (
                            ?, ?, ?, ?,
                            ?, ?, ?, ?,
                            ?, ?, ?, ?,
                            ?, ?, ?, ?,
                            ?, ?, ?,
                            UTC_TIMESTAMP(), UTC_TIMESTAMP()
                        )
                        """,
                        row.get("tenant_id"),
                        row.get("definition_id"),
                        row.get("trigger_binding_id"),
                        row.get("instance_id"),
                        row.get("definition_control_generation"),
                        row.get("provider_key"),
                        row.get("signal_key"),
                        row.get("schema_version"),
                        row.get("occurred_at"),
                        row.get("received_at"),
                        row.get("payload_json"),
                        row.get("payload_hash"),
                        row.get("process_status"),
                        row.get("attempt_count"),
                        row.get("max_attempts"),
                        row.get("next_attempt_at"),
                        row.get("processed_at"),
                        row.get("result_code"),
                        row.get("result_summary")));
        assertConstraintViolation(ex, "uk_signal_source");
    }

    private static void assertConstraintViolation(DataAccessException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        assertTrue(
                message != null && message.contains(constraintName),
                () -> "expected " + constraintName + ", got: " + message);
    }
}
