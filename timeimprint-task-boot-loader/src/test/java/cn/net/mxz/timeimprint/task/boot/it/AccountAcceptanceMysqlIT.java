package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.sms.client.CaptureSmsSender;
import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityBootstrap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * P06 U01—U10。走真实 MySQL 与捕获模式的短信、微信客户端，不调用腾讯云或微信开放平台。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class AccountAcceptanceMysqlIT {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(10_000);
    private static final String AUTH = "local-dev-authorization";

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

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
        registry.add("timeimprint.identity.sms.mode", () -> "capture");
        registry.add("timeimprint.identity.wechat.mode", () -> "capture");
    }

    @Test
    void u01RejectsBadExpiredAndDuplicateRegistration() throws Exception {
        String wrongPhone = phone();
        JsonNode wrong = post("/api/v1/users/register", Map.of(
                "phoneNumber", wrongPhone,
                "code", "000000",
                "password", "pass-word-1",
                "nickname", "错码"));
        assertEquals("INVALID_REQUEST", wrong.path("code").asText(), wrong.toString());
        assertEquals(0, countUsers(wrongPhone));

        String expiredPhone = phone();
        jdbc.update(
                """
                INSERT INTO tt_identity_verification_code(phone_number, code, created_at)
                VALUES (?, '654321', ?)
                """,
                expiredPhone,
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11)));
        JsonNode expired = post("/api/v1/users/register", Map.of(
                "phoneNumber", expiredPhone,
                "code", "654321",
                "password", "pass-word-1",
                "nickname", "过期"));
        assertEquals("INVALID_REQUEST", expired.path("code").asText(), expired.toString());
        assertEquals(0, countUsers(expiredPhone));

        String phone = phone();
        JsonNode first = register(phone, "第一次");
        assertEquals("OK", first.path("code").asText(), first.toString());
        sendSms(phone);
        JsonNode second = post("/api/v1/users/register", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-1",
                "nickname", "第二次"));
        assertEquals("STATE_CONFLICT", second.path("code").asText(), second.toString());
        assertEquals(1, countUsers(phone));
    }

    @Test
    void u02ChallengeThenTokenCanCallScenarioList() throws Exception {
        String phone = phone();
        JsonNode registered = register(phone, "待授权");
        JsonNode data = registered.path("data");
        assertTrue(data.path("authorizationRequired").asBoolean());
        assertTrue(data.path("challenge").isTextual());
        assertTrue(data.path("token").isNull());
        long userId = userId(data.path("actorKey").asText());
        assertEquals(0, sessionCount(userId));

        JsonNode wrong = post("/api/v1/users/login-authorization", Map.of(
                "challenge", data.path("challenge").asText(),
                "verificationString", "not-the-string"));
        assertEquals("UNAUTHENTICATED", wrong.path("code").asText(), wrong.toString());
        assertEquals(0, sessionCount(userId));

        JsonNode ok = post("/api/v1/users/login-authorization", Map.of(
                "challenge", data.path("challenge").asText(),
                "verificationString", AUTH));
        assertEquals("OK", ok.path("code").asText(), ok.toString());
        String token = ok.path("data").path("token").asText();
        assertFalse(token.isBlank());
        JsonNode scenarios = get("/api/v1/task-scenarios", token);
        assertEquals("OK", scenarios.path("code").asText(), scenarios.toString());
    }

    @Test
    void u03StoresHashAndRejectsMissingBadAndDisabledTokens() throws Exception {
        Issued issued = authorizeNew("令牌");
        byte[] stored = jdbc.queryForObject(
                "SELECT token_hash FROM tt_identity_session WHERE user_id = ?",
                byte[].class,
                issued.userId());
        assertNotNull(stored);
        assertEquals(32, stored.length);
        assertTrue(Arrays.equals(sha256(issued.token()), stored));
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains(issued.token()));

        JsonNode missing = raw("GET", "/api/v1/task-scenarios", null, null);
        assertEquals("UNAUTHENTICATED", missing.path("code").asText(), missing.toString());
        JsonNode bad = get("/api/v1/task-scenarios", "not-a-token");
        assertEquals("UNAUTHENTICATED", bad.path("code").asText(), bad.toString());

        JsonNode logout = post("/api/v1/users/logout", Map.of(), issued.token());
        assertEquals("OK", logout.path("code").asText(), logout.toString());
        JsonNode afterLogout = get("/api/v1/users/me", issued.token());
        assertEquals("UNAUTHENTICATED", afterLogout.path("code").asText(), afterLogout.toString());

        Issued disabled = authorizeNew("停用");
        jdbc.update(
                "UPDATE tt_identity_user SET account_status = 'DISABLED' WHERE user_id = ?",
                disabled.userId());
        JsonNode afterDisable = get("/api/v1/users/me", disabled.token());
        assertEquals("UNAUTHENTICATED", afterDisable.path("code").asText(), afterDisable.toString());
    }

    @Test
    void u04DebugHeaderAndBodyUserIdDoNotChangeActor() throws Exception {
        Issued issued = authorizeNew("调试头");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/v1/users/change-nickname")))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + issued.token())
                .header("X-Debug-Actor-Id", "actor-b")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"nickname\":\"改后\",\"userId\":\"actor-b\"}"))
                .build();
        JsonNode renamed = read(http.send(request, HttpResponse.BodyHandlers.ofString()));
        assertEquals("OK", renamed.path("code").asText(), renamed.toString());
        JsonNode me = get("/api/v1/users/me", issued.token());
        assertEquals(issued.actorKey(), me.path("data").path("actorKey").asText(), me.toString());
        assertNotEquals("actor-b", me.path("data").path("actorKey").asText());
        assertEquals("改后", me.path("data").path("nickname").asText());
    }

    @Test
    void u05WeChatFindsSameOpenIdAndRejectsSecondBind() throws Exception {
        String openid = "openid-" + UUID.randomUUID();
        JsonNode first = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", openid, "state", "state-1"));
        assertEquals("OK", first.path("code").asText(), first.toString());
        String actor = first.path("data").path("actorKey").asText();
        JsonNode again = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", openid, "state", "state-2"));
        assertEquals(actor, again.path("data").path("actorKey").asText(), again.toString());

        JsonNode blank = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "", "state", "state-3"));
        assertEquals("INVALID_REQUEST", blank.path("code").asText(), blank.toString());

        Issued other = authorizeNew("绑微信");
        JsonNode conflict = post("/api/v1/users/wechat/bind", Map.of(
                "appType", "web", "code", openid, "state", "state-4"), other.token());
        assertEquals("STATE_CONFLICT", conflict.path("code").asText(), conflict.toString());
    }

    @Test
    void u06BindPhoneMergesAndFailedBindLeavesSocialUntouched() throws Exception {
        String openid = "merge-" + UUID.randomUUID();
        Issued wechat = authorizeWeChat(openid);
        Issued phoneUser = authorizeNew("被合并");
        String phone = phoneOf(phoneUser.userId());
        sendSms(phone);
        JsonNode merged = post("/api/v1/users/wechat/bind-phone", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-1"), wechat.token());
        assertEquals("OK", merged.path("code").asText(), merged.toString());
        assertEquals(phoneUser.actorKey(), merged.path("data").path("actorKey").asText());
        assertEquals("DISABLED", statusOf(wechat.userId()));
        assertEquals(phoneUser.userId(), socialUserId(openid));

        String openid2 = "merge-fail-" + UUID.randomUUID();
        Issued temp = authorizeWeChat(openid2);
        Issued disabledDest = authorizeNew("已停用手机号");
        String disabledPhone = phoneOf(disabledDest.userId());
        jdbc.update(
                "UPDATE tt_identity_user SET account_status = 'DISABLED' WHERE user_id = ?",
                disabledDest.userId());
        sendSms(disabledPhone);
        JsonNode failed = post("/api/v1/users/wechat/bind-phone", Map.of(
                "phoneNumber", disabledPhone,
                "code", CaptureSmsSender.lastCode(disabledPhone),
                "password", "pass-word-1"), temp.token());
        assertEquals("INVALID_REQUEST", failed.path("code").asText(), failed.toString());
        assertEquals(temp.userId(), socialUserId(openid2));
        assertEquals("ACTIVE", statusOf(temp.userId()));
        assertEquals("DISABLED", statusOf(disabledDest.userId()));
    }

    @Test
    void u07RejectsUnbindWithoutPhoneOrPassword() throws Exception {
        String openid = "unbind-" + UUID.randomUUID();
        Issued wechat = authorizeWeChat(openid);
        JsonNode rejected = post("/api/v1/users/wechat/unbind", Map.of(), wechat.token());
        assertEquals("INVALID_REQUEST", rejected.path("code").asText(), rejected.toString());
        assertEquals(wechat.userId(), socialUserId(openid));

        String phone = phone();
        sendSms(phone);
        JsonNode bound = post("/api/v1/users/wechat/bind-phone", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-1"), wechat.token());
        assertEquals("OK", bound.path("code").asText(), bound.toString());
        JsonNode unbound = post("/api/v1/users/wechat/unbind", Map.of(), bound.path("data").path("token").asText());
        assertEquals("OK", unbound.path("code").asText(), unbound.toString());
        Integer left = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_identity_social_account WHERE openid = ?",
                Integer.class,
                openid);
        assertEquals(0, left);
    }

    @Test
    void u08FirstAccountIsAdminAndListHidesSecrets() throws Exception {
        Integer admin = jdbc.queryForObject(
                "SELECT is_admin FROM tt_identity_user WHERE actor_key = 'local-actor'",
                Integer.class);
        assertEquals(1, admin);
        Issued later = authorizeNew("普通用户");
        assertFalse(laterAdmin(later.userId()));
        JsonNode forbidden = get("/api/v1/users", later.token());
        assertEquals("FORBIDDEN", forbidden.path("code").asText(), forbidden.toString());

        JsonNode list = get("/api/v1/users", IdentityBootstrap.LOCAL_TOKEN);
        assertEquals("OK", list.path("code").asText(), list.toString());
        String body = list.toString();
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("password"));
        assertFalse(body.contains(IdentityBootstrap.LOCAL_TOKEN));
    }

    @Test
    void u09ActiveOwnerOnlyAndAccountsCannotSeeEachOther() throws Exception {
        Issued owner = authorizeNew("主人");
        JsonNode created = post("/api/v1/task-definitions", definitionBody(owner.actorKey(), "u09-visible"), owner.token());
        assertEquals("OK", created.path("code").asText(), created.toString());
        String definitionId = created.path("data").path("definitionId").asText();

        JsonNode missing = post(
                "/api/v1/task-definitions",
                definitionBody("does-not-exist", "u09-missing"),
                owner.token());
        assertEquals("INVALID_REQUEST", missing.path("code").asText(), missing.toString());

        Issued disabled = authorizeNew("已停用参与人");
        jdbc.update(
                "UPDATE tt_identity_user SET account_status = 'DISABLED' WHERE user_id = ?",
                disabled.userId());
        JsonNode disabledOwner = post(
                "/api/v1/task-definitions",
                definitionBody(disabled.actorKey(), "u09-disabled"),
                owner.token());
        assertEquals("INVALID_REQUEST", disabledOwner.path("code").asText(), disabledOwner.toString());

        Issued other = authorizeNew("旁观者");
        JsonNode hidden = get("/api/v1/task-definitions/" + definitionId, other.token());
        assertEquals("RESOURCE_NOT_FOUND", hidden.path("code").asText(), hidden.toString());
        JsonNode list = get("/api/v1/task-definitions?limit=50", other.token());
        assertEquals("OK", list.path("code").asText(), list.toString());
        for (JsonNode item : list.path("data").path("items")) {
            assertNotEquals(definitionId, item.path("definitionId").asText());
        }
        JsonNode own = get("/api/v1/task-definitions/" + definitionId, owner.token());
        assertEquals("OK", own.path("code").asText(), own.toString());
    }

    @Test
    void t04ChangePasswordAndReset() throws Exception {
        String phone = phone();
        Issued issued = authorizeNew(phone, "改密");
        String hashBefore = passwordHash(issued.userId());

        JsonNode missingToken = post("/api/v1/users/change-password", Map.of(
                "oldPassword", "pass-word-1",
                "password", "pass-word-2"));
        assertEquals("UNAUTHENTICATED", missingToken.path("code").asText(), missingToken.toString());

        JsonNode wrongOld = post("/api/v1/users/change-password", Map.of(
                "oldPassword", "wrong-old",
                "password", "pass-word-2"), issued.token());
        assertEquals("INVALID_REQUEST", wrongOld.path("code").asText(), wrongOld.toString());
        assertEquals(hashBefore, passwordHash(issued.userId()));

        JsonNode changed = post("/api/v1/users/change-password", Map.of(
                "oldPassword", "pass-word-1",
                "password", "pass-word-2"), issued.token());
        assertEquals("OK", changed.path("code").asText(), changed.toString());
        assertNotEquals(hashBefore, passwordHash(issued.userId()));
        JsonNode oldLogin = post("/api/v1/users/login", Map.of(
                "phoneNumber", phone,
                "password", "pass-word-1"));
        assertEquals("UNAUTHENTICATED", oldLogin.path("code").asText(), oldLogin.toString());
        JsonNode newLogin = post("/api/v1/users/login", Map.of(
                "phoneNumber", phone,
                "password", "pass-word-2"));
        assertEquals("OK", newLogin.path("code").asText(), newLogin.toString());
        assertFalse(newLogin.path("data").path("token").asText().isBlank());

        String unknown = phone();
        sendSms(unknown);
        int usersBefore = countAllUsers();
        JsonNode noUser = post("/api/v1/users/password-reset", Map.of(
                "phoneNumber", unknown,
                "code", CaptureSmsSender.lastCode(unknown),
                "password", "pass-word-3"));
        assertEquals("INVALID_REQUEST", noUser.path("code").asText(), noUser.toString());
        assertEquals(usersBefore, countAllUsers());
        assertEquals(0, countUsers(unknown));

        sendSms(phone);
        jdbc.update(
                """
                UPDATE tt_identity_verification_code
                SET created_at = ?
                WHERE phone_number = ? AND code = ?
                """,
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11)),
                phone,
                CaptureSmsSender.lastCode(phone));
        String hashAfterChange = passwordHash(issued.userId());
        JsonNode expired = post("/api/v1/users/password-reset", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-3"));
        assertEquals("INVALID_REQUEST", expired.path("code").asText(), expired.toString());
        assertEquals(hashAfterChange, passwordHash(issued.userId()));

        sendSms(phone);
        JsonNode reset = post("/api/v1/users/password-reset", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-3"));
        assertEquals("OK", reset.path("code").asText(), reset.toString());
        JsonNode stale = post("/api/v1/users/login", Map.of(
                "phoneNumber", phone,
                "password", "pass-word-2"));
        assertEquals("UNAUTHENTICATED", stale.path("code").asText(), stale.toString());
        JsonNode resetLogin = post("/api/v1/users/login", Map.of(
                "phoneNumber", phone,
                "password", "pass-word-3"));
        assertEquals("OK", resetLogin.path("code").asText(), resetLogin.toString());
        assertFalse(resetLogin.path("data").path("token").asText().isBlank());
    }

    private JsonNode register(String phone, String nickname) throws Exception {
        sendSms(phone);
        return post("/api/v1/users/register", Map.of(
                "phoneNumber", phone,
                "code", CaptureSmsSender.lastCode(phone),
                "password", "pass-word-1",
                "nickname", nickname));
    }

    private Issued authorizeNew(String nickname) throws Exception {
        return authorizeNew(phone(), nickname);
    }

    private Issued authorizeNew(String phone, String nickname) throws Exception {
        JsonNode registered = register(phone, nickname);
        assertEquals("OK", registered.path("code").asText(), registered.toString());
        return finishAuthorization(registered);
    }

    private Issued authorizeWeChat(String openid) throws Exception {
        JsonNode created = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", openid, "state", "state-" + openid));
        assertEquals("OK", created.path("code").asText(), created.toString());
        return finishAuthorization(created);
    }

    private Issued finishAuthorization(JsonNode login) throws Exception {
        JsonNode data = login.path("data");
        JsonNode ok = post("/api/v1/users/login-authorization", Map.of(
                "challenge", data.path("challenge").asText(),
                "verificationString", AUTH));
        assertEquals("OK", ok.path("code").asText(), ok.toString());
        String actorKey = ok.path("data").path("actorKey").asText();
        return new Issued(ok.path("data").path("token").asText(), actorKey, userId(actorKey));
    }

    private void sendSms(String phone) throws Exception {
        JsonNode sent = post("/api/v1/users/sms", Map.of("phoneNumber", phone));
        assertEquals("OK", sent.path("code").asText(), sent.toString());
    }

    private Map<String, Object> definitionBody(String principalId, String title) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime occurrence = ZonedDateTime.now(zone).plusDays(2).withNano(0);
        return Map.of(
                "requestId", UUID.randomUUID().toString(),
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "title", title,
                "description", "u09",
                "scenarioConfig", Map.of(),
                "participants",
                List.of(Map.of("principalType", "USER", "principalId", principalId, "roleCode", "OWNER")),
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config",
                        Map.of(
                                "type", "ONCE",
                                "localDate", LocalDate.from(occurrence).toString(),
                                "localTime", TIME_FMT.format(LocalTime.from(occurrence)),
                                "zoneId", "Asia/Shanghai"))));
    }

    private String passwordHash(long userId) {
        return jdbc.queryForObject(
                "SELECT password_hash FROM tt_identity_user WHERE user_id = ?",
                String.class,
                userId);
    }

    private int countAllUsers() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tt_identity_user", Integer.class);
        return n == null ? 0 : n;
    }

    private int countUsers(String phone) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_identity_user WHERE phone_number = ?",
                Integer.class,
                phone);
        return n == null ? 0 : n;
    }

    private int sessionCount(long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_identity_session WHERE user_id = ?",
                Integer.class,
                userId);
        return n == null ? 0 : n;
    }

    private long userId(String actorKey) {
        Long id = jdbc.queryForObject(
                "SELECT user_id FROM tt_identity_user WHERE actor_key = ?",
                Long.class,
                actorKey);
        assertNotNull(id);
        return id;
    }

    private String phoneOf(long userId) {
        return jdbc.queryForObject(
                "SELECT phone_number FROM tt_identity_user WHERE user_id = ?",
                String.class,
                userId);
    }

    private String statusOf(long userId) {
        return jdbc.queryForObject(
                "SELECT account_status FROM tt_identity_user WHERE user_id = ?",
                String.class,
                userId);
    }

    private long socialUserId(String openid) {
        Long id = jdbc.queryForObject(
                "SELECT user_id FROM tt_identity_social_account WHERE openid = ?",
                Long.class,
                openid);
        assertNotNull(id);
        return id;
    }

    private boolean laterAdmin(long userId) {
        Integer admin = jdbc.queryForObject(
                "SELECT is_admin FROM tt_identity_user WHERE user_id = ?",
                Integer.class,
                userId);
        return admin != null && admin == 1;
    }

    private static String phone() {
        int n = PHONE_SEQ.incrementAndGet() % 10;
        long mixed = (System.currentTimeMillis() % 100_000_000L) * 10 + n;
        return "13" + String.format("%09d", mixed);
    }

    private JsonNode get(String path, String token) throws Exception {
        return raw("GET", path, token, null);
    }

    private JsonNode post(String path, Object body) throws Exception {
        return post(path, body, null);
    }

    private JsonNode post(String path, Object body, String token) throws Exception {
        return raw("POST", path, token, objectMapper.writeValueAsString(body));
    }

    private JsonNode raw(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json));
        } else {
            builder.GET();
        }
        return read(http.send(builder.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private JsonNode read(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static byte[] sha256(String raw) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record Issued(String token, String actorKey, long userId) {}
}
