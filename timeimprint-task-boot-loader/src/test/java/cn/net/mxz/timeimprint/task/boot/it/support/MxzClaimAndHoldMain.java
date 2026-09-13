package cn.net.mxz.timeimprint.task.boot.it.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Second-JVM helper for dual-process crash takeover ITs.
 *
 * <p>Claims a Signal or Action with a fresh executionToken, writes the token to a file, then parks
 * until killed. Uses plain JDBC so the child process does not start Spring workers.
 *
 * <p>Args: {@code signal|action} {@code id} {@code tokenFile} {@code jdbcUrl} {@code user} {@code
 * password} [{@code leaseSeconds}] — leaseSeconds defaults to 300 (A04/A20 hold); pass 10 for §6
 * wall-clock SLA.
 */
public final class MxzClaimAndHoldMain {

    private MxzClaimAndHoldMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println(
                    "usage: MxzClaimAndHoldMain signal|action <id> <tokenFile> <jdbcUrl> <user> <password> [leaseSeconds]");
            System.exit(2);
        }
        String mode = args[0];
        long id = Long.parseLong(args[1]);
        Path tokenFile = Path.of(args[2]);
        String jdbcUrl = args[3];
        String user = args[4];
        String password = args[5];
        int leaseSeconds = args.length >= 7 ? Integer.parseInt(args[6]) : 300;
        String token = UUID.randomUUID().toString();
        String owner = "crash-child-" + ProcessHandle.current().pid();

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password)) {
            c.setAutoCommit(false);
            int claimed;
            if ("signal".equals(mode)) {
                claimed = claimSignal(c, id, owner, token, leaseSeconds);
            } else if ("action".equals(mode)) {
                claimed = claimAction(c, id, owner, token, leaseSeconds);
            } else {
                System.err.println("unknown mode: " + mode);
                System.exit(2);
                return;
            }
            if (claimed != 1) {
                System.err.println("claim failed for " + mode + " id=" + id);
                c.rollback();
                System.exit(3);
            }
            c.commit();
        }

        Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
        System.out.println("CLAIMED token=" + token + " leaseSeconds=" + leaseSeconds);
        // Park until parent kills this process.
        Thread.sleep(Long.MAX_VALUE);
    }

    private static int claimSignal(
            Connection c, long signalId, String owner, String token, int leaseSeconds) throws Exception {
        try (PreparedStatement ps =
                c.prepareStatement(
                        """
                        UPDATE tt_task_signal SET
                          process_status = 'RUNNING',
                          lease_owner = ?,
                          lease_until = UTC_TIMESTAMP() + INTERVAL ? SECOND,
                          execution_token = ?,
                          attempt_count = attempt_count + 1,
                          updated_at = UTC_TIMESTAMP()
                        WHERE signal_id = ? AND process_status IN ('READY', 'RETRY_WAIT')
                        """)) {
            ps.setString(1, owner);
            ps.setInt(2, leaseSeconds);
            ps.setString(3, token);
            ps.setLong(4, signalId);
            return ps.executeUpdate();
        }
    }

    private static int claimAction(
            Connection c, long actionJobId, String owner, String token, int leaseSeconds)
            throws Exception {
        try (PreparedStatement ps =
                c.prepareStatement(
                        """
                        UPDATE tt_action_job SET
                          status = 'RUNNING',
                          lease_owner = ?,
                          lease_until = UTC_TIMESTAMP() + INTERVAL ? SECOND,
                          execution_token = ?,
                          attempt_count = attempt_count + 1,
                          updated_at = UTC_TIMESTAMP()
                        WHERE action_job_id = ? AND status IN ('READY', 'RETRY_WAIT')
                        """)) {
            ps.setString(1, owner);
            ps.setInt(2, leaseSeconds);
            ps.setString(3, token);
            ps.setLong(4, actionJobId);
            int n = ps.executeUpdate();
            if (n != 1) {
                return n;
            }
        }
        int attemptNo = 1;
        try (PreparedStatement max =
                c.prepareStatement(
                        "SELECT COALESCE(MAX(attempt_no), 0) FROM tt_action_attempt WHERE action_job_id = ?")) {
            max.setLong(1, actionJobId);
            try (ResultSet rs = max.executeQuery()) {
                if (rs.next()) {
                    attemptNo = rs.getInt(1) + 1;
                }
            }
        }
        try (PreparedStatement ins =
                c.prepareStatement(
                        """
                        INSERT INTO tt_action_attempt(
                          action_job_id, attempt_no, execution_token, started_at)
                        VALUES (?, ?, ?, UTC_TIMESTAMP())
                        """)) {
            ins.setLong(1, actionJobId);
            ins.setInt(2, attemptNo);
            ins.setString(3, token);
            ins.executeUpdate();
        }
        return 1;
    }
}
