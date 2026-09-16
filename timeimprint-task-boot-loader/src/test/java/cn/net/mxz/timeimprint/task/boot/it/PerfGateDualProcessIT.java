package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import cn.net.mxz.timeimprint.task.boot.it.support.PerfWorkerMain;

/**
 * 07 §6 本地性能门槛：真时钟 + 真 MySQL + 两进程；预建 1 万 ACTIVE 定义；
 * 60s 内均匀到期 1000 个 S01；预热 1 次 + 独立 runId 正式 3 次。
 *
 * <p>不得放宽门槛后标 PASS。失败时保留各次 P50/P95/P99 与积压清空时间供 DELIVERY。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("dual-process-it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 50, unit = TimeUnit.MINUTES)
class PerfGateDualProcessIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String JDBC =
            "jdbc:mysql://127.0.0.1:13306/timeimprint_task_perf?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC";
    private static final String DB_USER = "tit";
    private static final String DB_PASS = "tit_local";

    private static final int BACKGROUND_DEFS = 9_000;
    private static final int TARGET_DEFS = 1_000;
    private static final int TOTAL_ACTIVE = BACKGROUND_DEFS + TARGET_DEFS;
    private static final int FORMAL_RUNS = 3;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final Duration DRAIN = Duration.ofSeconds(30);
    private static final Duration TRANSITION_P95_MAX = Duration.ofSeconds(5);
    private static final Duration INBOX_P95_MAX = Duration.ofSeconds(10);
    private static final int CREATE_PARALLELISM = 32;
    private static final int CLAIM_BATCH = 100;

    private static final String SUITE_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final List<String> RUN_REPORTS = new ArrayList<>();
    private static Process child;
    private static Path childLog;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> JDBC);
        r.add("spring.datasource.username", () -> DB_USER);
        r.add("spring.datasource.password", () -> DB_PASS);
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
        r.add("INSTANCE_ID", () -> "perf-parent-" + SUITE_ID);
        r.add("server.address", () -> "127.0.0.1");
        r.add("CLAIM_BATCH_SIZE", () -> Integer.toString(CLAIM_BATCH));
        r.add("spring.task.scheduling.enabled", () -> "true");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "24");
        r.add("spring.datasource.hikari.minimum-idle", () -> "4");
    }

    @BeforeAll
    void startChildWorker() throws Exception {
        childLog = Files.createTempFile("perf-child-", ".log");
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(PerfWorkerMain.class.getName());
        cmd.add("--server.port=0");
        cmd.add("--server.address=127.0.0.1");
        cmd.add("--spring.datasource.url=" + JDBC);
        cmd.add("--spring.datasource.username=" + DB_USER);
        cmd.add("--spring.datasource.password=" + DB_PASS);
        cmd.add("--timeimprint.local.tenant-id=local-tenant");
        cmd.add("--timeimprint.local.actor-id=local-actor");
        cmd.add("--CLAIM_BATCH_SIZE=" + CLAIM_BATCH);
        cmd.add("--spring.datasource.hikari.maximum-pool-size=16");
        cmd.add("--spring.datasource.hikari.minimum-idle=2");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("INSTANCE_ID", "perf-child-" + SUITE_ID);
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        pb.redirectErrorStream(true);
        pb.redirectOutput(childLog.toFile());
        child = pb.start();
        awaitChildStarted(Duration.ofSeconds(90));
    }

    @AfterAll
    void stopChildWorker() throws Exception {
        if (child != null) {
            child.destroyForcibly();
            child.waitFor(20, TimeUnit.SECONDS);
        }
        if (childLog != null) {
            System.out.println("perf child log: " + childLog);
        }
        for (String line : RUN_REPORTS) {
            System.out.println(line);
        }
    }

    @Test
    @Order(1)
    void seedTenThousandActiveDefinitions() throws Exception {
        Instant seedStart = Instant.now();
        ZonedDateTime far = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(7).withNano(0);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(CREATE_PARALLELISM);
        try {
            List<Future<?>> futures = new ArrayList<>(BACKGROUND_DEFS);
            for (int i = 0; i < BACKGROUND_DEFS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        createOnce("perf-" + SUITE_ID + "-bg-" + idx, far);
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        Duration seedTook = Duration.between(seedStart, Instant.now());
        String report = "seed background="
                + BACKGROUND_DEFS
                + " ok="
                + ok.get()
                + " fail="
                + fail.get()
                + " took="
                + seedTook
                + " suite="
                + SUITE_ID;
        RUN_REPORTS.add(report);
        System.out.println(report);
        assertEquals(0, fail.get(), report);
        assertEquals(BACKGROUND_DEFS, ok.get(), report);
        assertEquals(TOTAL_ACTIVE, BACKGROUND_DEFS + TARGET_DEFS);
    }

    @Test
    @Order(2)
    void warmupAndThreeFormalRunsMeetGates() throws Exception {
        List<RunResult> results = new ArrayList<>();
        results.add(executeRun("warmup", true));
        for (int i = 1; i <= FORMAL_RUNS; i++) {
            results.add(executeRun("formal-" + i, false));
        }

        StringBuilder all = new StringBuilder();
        for (RunResult r : results) {
            all.append(r.report).append('\n');
            RUN_REPORTS.add(r.report);
        }
        System.out.println(all);

        List<RunResult> formals = results.stream().filter(r -> !r.warmup).toList();
        assertEquals(FORMAL_RUNS, formals.size());
        for (RunResult r : formals) {
            if (!r.passed) {
                fail("formal run failed gates:\n" + all);
            }
        }
    }

    private RunResult executeRun(String label, boolean warmup) throws Exception {
        String runId = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        String titlePrefix = "perf-" + SUITE_ID + "-" + runId + "-tgt-";

        // Leave headroom for parallel create of 1000 targets before first due.
        Instant windowStart = Instant.now().plusSeconds(90).truncatedTo(ChronoUnit.SECONDS);
        Instant seedStart = Instant.now();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(CREATE_PARALLELISM);
        try {
            List<Future<?>> futures = new ArrayList<>(TARGET_DEFS);
            for (int i = 0; i < TARGET_DEFS; i++) {
                final int idx = i;
                Instant occurrenceAt = windowStart.plusMillis(idx * WINDOW.toMillis() / TARGET_DEFS);
                ZonedDateTime local = occurrenceAt.atZone(ZoneId.of("Asia/Shanghai"));
                futures.add(pool.submit(() -> {
                    try {
                        createOnce(titlePrefix + idx, local);
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get(25, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        Duration createTook = Duration.between(seedStart, Instant.now());
        assertEquals(0, fail.get(), "target create failures runId=" + runId);
        assertEquals(TARGET_DEFS, ok.get());
        assertTrue(
                Instant.now().isBefore(windowStart),
                "target create overran windowStart; createTook=" + createTook + " windowStart=" + windowStart);

        Instant lastDue = windowStart.plus(WINDOW);
        Instant drainDeadline = lastDue.plus(DRAIN);
        Instant waitStart = Instant.now();
        while (Instant.now().isBefore(drainDeadline.plusSeconds(5))) {
            Integer inbox = countInbox(titlePrefix);
            if (inbox != null && inbox >= TARGET_DEFS) {
                Integer backlog = countBacklog(titlePrefix);
                if (backlog != null && backlog == 0) {
                    break;
                }
            }
            Thread.sleep(1000);
        }
        Duration wallToClear = Duration.between(waitStart, Instant.now());
        Instant clearedAt = Instant.now();

        Metrics m = collectMetrics(titlePrefix);
        boolean inboxOk = m.successCount == TARGET_DEFS && m.duplicateInbox == 0;
        boolean terminalOk = m.deadOrExpired == 0;
        boolean p95TransitionOk = m.p95TransitionMs <= TRANSITION_P95_MAX.toMillis();
        boolean p95InboxOk = m.p95InboxMs <= INBOX_P95_MAX.toMillis();
        boolean drainOk = clearedAt.isBefore(drainDeadline.plusSeconds(2)) && m.backlogAtCollect == 0;
        boolean passed = inboxOk && terminalOk && p95TransitionOk && p95InboxOk && drainOk;

        String report = String.format(
                "run=%s warmup=%s runId=%s createTook=%s wallToClear=%s inbox=%d dup=%d deadExpired=%d "
                        + "backlog=%d p50t=%dms p95t=%dms p99t=%dms p50i=%dms p95i=%dms p99i=%dms "
                        + "passed=%s gates={inboxOk=%s terminalOk=%s p95t<=%ds=%s p95i<=%ds=%s drain30s=%s}",
                label,
                warmup,
                runId,
                createTook,
                wallToClear,
                m.successCount,
                m.duplicateInbox,
                m.deadOrExpired,
                m.backlogAtCollect,
                m.p50TransitionMs,
                m.p95TransitionMs,
                m.p99TransitionMs,
                m.p50InboxMs,
                m.p95InboxMs,
                m.p99InboxMs,
                passed,
                inboxOk,
                terminalOk,
                TRANSITION_P95_MAX.toSeconds(),
                p95TransitionOk,
                INBOX_P95_MAX.toSeconds(),
                p95InboxOk,
                drainOk);
        return new RunResult(warmup, passed, report);
    }

    private Metrics collectMetrics(String titlePrefix) {
        String like = titlePrefix + "%";
        List<Long> transitionLatencies = new ArrayList<>();
        List<Long> inboxLatencies = new ArrayList<>();
        jdbc.query(
                """
                SELECT i.occurrence_at AS occ,
                       (SELECT MIN(t.created_at) FROM tt_task_transition t
                         WHERE t.instance_id = i.instance_id AND t.source_type = 'SIGNAL') AS tr,
                       (SELECT MIN(ib.created_at) FROM tt_inbox ib
                         WHERE ib.instance_id = i.instance_id) AS ib,
                       (SELECT COUNT(*) FROM tt_inbox ib2 WHERE ib2.instance_id = i.instance_id) AS ib_cnt
                FROM tt_task_instance i
                JOIN tt_task_definition d ON d.definition_id = i.definition_id
                WHERE d.title LIKE ?
                """,
                rs -> {
                    while (rs.next()) {
                        LocalDateTime occ = rs.getObject("occ", LocalDateTime.class);
                        LocalDateTime tr = rs.getObject("tr", LocalDateTime.class);
                        LocalDateTime ib = rs.getObject("ib", LocalDateTime.class);
                        int ibCnt = rs.getInt("ib_cnt");
                        if (occ == null || tr == null || ib == null || ibCnt != 1) {
                            continue;
                        }
                        Instant occI = occ.toInstant(ZoneOffset.UTC);
                        Instant trI = tr.toInstant(ZoneOffset.UTC);
                        Instant ibI = ib.toInstant(ZoneOffset.UTC);
                        transitionLatencies.add(Duration.between(occI, trI).toMillis());
                        inboxLatencies.add(Duration.between(occI, ibI).toMillis());
                    }
                    return null;
                },
                like);

        Integer dup = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM (
                  SELECT i.instance_id
                  FROM tt_task_instance i
                  JOIN tt_task_definition d ON d.definition_id = i.definition_id
                  JOIN tt_inbox ib ON ib.instance_id = i.instance_id
                  WHERE d.title LIKE ?
                  GROUP BY i.instance_id
                  HAVING COUNT(*) > 1
                ) x
                """,
                Integer.class,
                like);
        Integer dead = jdbc.queryForObject(
                """
                SELECT
                  (SELECT COUNT(*) FROM tt_task_signal s
                    JOIN tt_task_definition d ON d.definition_id = s.definition_id
                    WHERE d.title LIKE ? AND s.process_status IN ('DEAD','EXPIRED'))
                  +
                  (SELECT COUNT(*) FROM tt_action_job a
                    JOIN tt_task_definition d ON d.definition_id = a.definition_id
                    WHERE d.title LIKE ? AND a.status IN ('DEAD','EXPIRED'))
                """,
                Integer.class,
                like,
                like);
        Integer backlog = countBacklog(titlePrefix);

        return new Metrics(
                transitionLatencies.size(),
                dup == null ? -1 : dup,
                dead == null ? -1 : dead,
                backlog == null ? -1 : backlog,
                percentileNearestRank(transitionLatencies, 0.50),
                percentileNearestRank(transitionLatencies, 0.95),
                percentileNearestRank(transitionLatencies, 0.99),
                percentileNearestRank(inboxLatencies, 0.50),
                percentileNearestRank(inboxLatencies, 0.95),
                percentileNearestRank(inboxLatencies, 0.99));
    }

    private Integer countInbox(String titlePrefix) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_inbox ib
                JOIN tt_task_definition d ON d.definition_id = ib.definition_id
                WHERE d.title LIKE ?
                """,
                Integer.class,
                titlePrefix + "%");
    }

    private Integer countBacklog(String titlePrefix) {
        return jdbc.queryForObject(
                """
                SELECT
                  (SELECT COUNT(*) FROM tt_task_signal s
                    JOIN tt_task_definition d ON d.definition_id = s.definition_id
                    WHERE d.title LIKE ? AND s.process_status IN ('READY','RUNNING','RETRY_WAIT'))
                  +
                  (SELECT COUNT(*) FROM tt_action_job a
                    JOIN tt_task_definition d ON d.definition_id = a.definition_id
                    WHERE d.title LIKE ? AND a.status IN ('READY','RUNNING','RETRY_WAIT'))
                """,
                Integer.class,
                titlePrefix + "%",
                titlePrefix + "%");
    }

    /** 最近秩：ceil(p * n) 取 1-based，再转 0-based 下标。 */
    private static long percentileNearestRank(List<Long> values, double p) {
        if (values.isEmpty()) {
            return Long.MAX_VALUE;
        }
        long[] sorted = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p * sorted.length);
        int idx = Math.min(sorted.length, Math.max(1, rank)) - 1;
        return sorted[idx];
    }

    private void awaitChildStarted(Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!child.isAlive()) {
                String log = Files.exists(childLog) ? Files.readString(childLog) : "";
                fail("perf child exited early:\n" + log);
            }
            if (Files.exists(childLog)) {
                String log = Files.readString(childLog);
                if (log.contains("Started TimeImprintTaskApplication")
                        || log.contains("Tomcat started on port")) {
                    // Workers have initialDelay up to 7s; give them a moment.
                    Thread.sleep(8000);
                    return;
                }
            }
            Thread.sleep(500);
        }
        String log = Files.exists(childLog) ? Files.readString(childLog) : "";
        fail("perf child did not start within " + timeout + ":\n" + log);
    }

    private void createOnce(String title, ZonedDateTime occurrence) throws Exception {
        Map<String, Object> body = Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "scenarioKey",
                "reminder",
                "scenarioSchemaVersion",
                1,
                "title",
                title,
                "description",
                "07 §6 perf",
                "scenarioConfig",
                Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", "local-actor", "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey",
                        "primary",
                        "providerKey",
                        "calendar",
                        "schemaVersion",
                        1,
                        "config",
                        Map.of(
                                "type",
                                "ONCE",
                                "localDate",
                                occurrence.toLocalDate().toString(),
                                "localTime",
                                TIME_FMT.format(occurrence.toLocalTime()),
                                "zoneId",
                                "Asia/Shanghai"))));
        JsonNode created = post("/api/v1/task-definitions", body);
        if (!"OK".equals(created.path("code").asText())) {
            throw new IllegalStateException("create failed: " + created);
        }
    }

    private JsonNode post(String path, Object body) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private record RunResult(boolean warmup, boolean passed, String report) {}

    private record Metrics(
            int successCount,
            int duplicateInbox,
            int deadOrExpired,
            int backlogAtCollect,
            long p50TransitionMs,
            long p95TransitionMs,
            long p99TransitionMs,
            long p50InboxMs,
            long p95InboxMs,
            long p99InboxMs) {}
}
