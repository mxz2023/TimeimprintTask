package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatProfile;
import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P07 W01—W03：live 网页扫码与 state；微信 HTTP 由测试替身完成。 */
@SpringBootTest(
        classes = {TimeImprintTaskApplication.class, WeChatQrMysqlIT.LiveWeChatTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class WeChatQrMysqlIT {

    private static final String APP_ID = "wx-it-app-id";
    private static final String REDIRECT = "https://example.com/wechat/callback";
    static final AtomicInteger WECHAT_EXCHANGE_CALLS = new AtomicInteger();

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
        registry.add("timeimprint.identity.sms.mode", () -> "capture");
        registry.add("timeimprint.identity.wechat.mode", () -> "live");
        registry.add("timeimprint.identity.wechat.web.app-id", () -> APP_ID);
        registry.add("timeimprint.identity.wechat.web.app-secret", () -> "wx-it-secret");
        registry.add("timeimprint.identity.wechat.web.redirect-uri", () -> REDIRECT);
    }

    @BeforeEach
    void resetCalls() {
        WECHAT_EXCHANGE_CALLS.set(0);
    }

    @Test
    void w01ConfigIssuesQrConnectWithoutSecretAndRejectsMobileAppType() throws Exception {
        JsonNode web = get("/api/v1/users/wechat/config?appType=web");
        assertEquals("OK", web.path("code").asText(), web.toString());
        String authUrl = web.path("data").path("authUrl").asText();
        String state = web.path("data").path("state").asText();
        assertTrue(authUrl.contains("open.weixin.qq.com/connect/qrconnect"));
        assertTrue(authUrl.contains("appid=" + APP_ID));
        assertFalse(authUrl.contains("secret"));
        assertFalse(web.toString().contains("appSecret"));
        assertFalse(state.isBlank());
        assertNotEquals("capture-state", state);

        JsonNode ios = get("/api/v1/users/wechat/config?appType=ios");
        assertEquals("INVALID_REQUEST", ios.path("code").asText(), ios.toString());
        JsonNode android = get("/api/v1/users/wechat/config?appType=android");
        assertEquals("INVALID_REQUEST", android.path("code").asText(), android.toString());
    }

    @Test
    void w02RejectsBadExpiredAndReusedStateWithoutCallingWeChat() throws Exception {
        JsonNode bad = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "code-1", "state", "not-in-db"));
        assertEquals("INVALID_REQUEST", bad.path("code").asText(), bad.toString());
        assertEquals(0, WECHAT_EXCHANGE_CALLS.get());

        String expiredState = "expired-state-value";
        jdbc.update(
                """
                INSERT INTO tt_identity_oauth_state(state_hash, app_type, expires_at, created_at)
                VALUES (?, 'web', ?, UTC_TIMESTAMP())
                """,
                sha256(expiredState),
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        JsonNode expired = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "code-2", "state", expiredState));
        assertEquals("INVALID_REQUEST", expired.path("code").asText(), expired.toString());
        assertEquals(0, WECHAT_EXCHANGE_CALLS.get());

        JsonNode config = get("/api/v1/users/wechat/config?appType=web");
        String state = config.path("data").path("state").asText();
        JsonNode first = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "openid-a", "state", state));
        assertEquals("OK", first.path("code").asText(), first.toString());
        assertEquals(1, WECHAT_EXCHANGE_CALLS.get());

        WECHAT_EXCHANGE_CALLS.set(0);
        JsonNode second = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "openid-b", "state", state));
        assertEquals("INVALID_REQUEST", second.path("code").asText(), second.toString());
        assertEquals(0, WECHAT_EXCHANGE_CALLS.get());
    }

    @Test
    void w03LiveStubOpenIdLogsInWithoutAccessTokenInResponse() throws Exception {
        JsonNode config = get("/api/v1/users/wechat/config?appType=web");
        String state = config.path("data").path("state").asText();
        JsonNode login = post("/api/v1/users/wechat/callback", Map.of(
                "appType", "web", "code", "stub-code-99", "state", state));
        assertEquals("OK", login.path("code").asText(), login.toString());
        assertEquals(1, WECHAT_EXCHANGE_CALLS.get());
        String body = login.toString();
        assertFalse(body.contains("access_token"));
        assertFalse(body.contains("secret-access-token"));
        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM tt_identity_social_account WHERE openid = ?",
                Long.class,
                "live-openid-stub-code-99");
        assertTrue(userId != null && userId > 0);
    }

    @TestConfiguration
    static class LiveWeChatTestConfig {
        @Bean
        @Primary
        WeChatOAuthClient testWeChatOAuthClient() {
            return (appType, code, state) -> {
                WECHAT_EXCHANGE_CALLS.incrementAndGet();
                return new WeChatProfile(appType, "live-openid-" + code, "", "测试昵称", "");
            };
        }
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return objectMapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return objectMapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static byte[] sha256(String raw) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
    }
}
