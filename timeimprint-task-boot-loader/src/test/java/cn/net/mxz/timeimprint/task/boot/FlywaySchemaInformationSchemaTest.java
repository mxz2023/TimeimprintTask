package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * G01 / T03 helper: expected Flyway table set for information_schema assertions.
 * Full JDBC checks run under {@code mysql-it} once a datasource fixture is wired.
 */
public class FlywaySchemaInformationSchemaTest {

    public static List<String> expectedTableNames() {
        return List.of(
                "tt_task_definition",
                "tt_trigger_binding",
                "tt_task_instance",
                "tt_task_participant",
                "tt_task_signal",
                "tt_task_transition",
                "tt_action_job",
                "tt_action_attempt",
                "tt_command_dedup",
                "tt_audit_log",
                "tt_notification",
                "tt_inbox");
    }

    public static Set<String> queryTableNames(Connection connection, String schema) throws Exception {
        try (var statement = connection.prepareStatement(
                """
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_TYPE = 'BASE TABLE'
                  AND TABLE_NAME LIKE 'tt\\_%'
                ORDER BY TABLE_NAME
                """)) {
            statement.setString(1, schema);
            try (var rs = statement.executeQuery()) {
                var names = new java.util.ArrayList<String>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return Set.copyOf(names);
            }
        }
    }

    public     static void assertExpectedTablesPresent(Connection connection, String schema) throws Exception {
        Set<String> actual = queryTableNames(connection, schema);
        Set<String> expected = Set.copyOf(expectedTableNames());
        if (!actual.equals(expected)) {
            var missing = expected.stream().filter(n -> !actual.contains(n)).sorted().toList();
            var extra = actual.stream().filter(n -> !expected.contains(n)).sorted().toList();
            throw new AssertionError(
                    "schema mismatch: missing=" + missing + ", extra=" + extra);
        }
    }

    /** A38: require all 12 platform tables; ignore unrelated tables in shared test DBs. */
    public static void assertRequiredPlatformTablesPresent(Connection connection, String schema) throws Exception {
        Set<String> actual = queryTableNames(connection, schema);
        var missing =
                expectedTableNames().stream().filter(n -> !actual.contains(n)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("missing platform tables: " + missing);
        }
    }

    @Test
    void contractListsTwelveTables() {
        assertEquals(12, expectedTableNames().size());
        assertEquals(
                expectedTableNames().stream().sorted().collect(Collectors.toList()),
                expectedTableNames().stream().sorted().toList());
    }

    @Test
    @Tag("mysql-it")
    @Disabled("Await mysql-it Flyway fixture and JDBC connection")
    void informationSchemaMatchesExpectedTables() {
        // When enabled: obtain Connection + schema from test container, then:
        // assertExpectedTablesPresent(connection, schema);
    }
}
