package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuMockServerFixture;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuAdapterProperties;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuCredentials;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CachingFeishuTokenProviderTest {

    private static String tokenBody(String token, int expire) {
        return "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"" + token + "\",\"expire\":" + expire + "}";
    }

    private FeishuMockServerFixture server;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-06T00:00:00Z"));
    private CachingFeishuTokenProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new FeishuMockServerFixture();
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        provider = new CachingFeishuTokenProvider(
                new FeishuAdapterProperties(server.baseUrl() + "/", 2),
                new FeishuCredentials("cli_app", "secret_x"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                JsonMapper.builder().build(),
                clock);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void cachesUntilRefreshMarginThenRefreshes() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, tokenBody("t-1", 7200));
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, tokenBody("t-2", 7200));

        assertEquals("t-1", provider.tenantAccessToken());
        now.set(now.get().plusSeconds(7200 - 61));
        assertEquals("t-1", provider.tenantAccessToken());
        assertEquals(1, server.requestsTo(FeishuMockServerFixture.TOKEN_PATH).size());

        now.set(now.get().plusSeconds(2));
        assertEquals("t-2", provider.tenantAccessToken());
        assertEquals(2, server.requestsTo(FeishuMockServerFixture.TOKEN_PATH).size());
    }

    @Test
    void invalidateForcesRefetch() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, tokenBody("t-1", 7200));
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, tokenBody("t-2", 7200));
        assertEquals("t-1", provider.tenantAccessToken());
        provider.invalidate();
        assertEquals("t-2", provider.tenantAccessToken());
    }

    @Test
    void rejectedCredentialsArePermanentAndRateLimitIsRetryable() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 400, "{\"code\":10003,\"msg\":\"invalid app\"}");
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, "{\"code\":99991400,\"msg\":\"rate\"}");
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 502, "");
        FeishuAuthException permanent = assertThrows(FeishuAuthException.class, provider::tenantAccessToken);
        assertFalse(permanent.retryable());
        assertEquals(10003, permanent.apiCode());
        assertFalse(permanent.getMessage().contains("secret_x"));
        assertTrue(assertThrows(FeishuAuthException.class, provider::tenantAccessToken).retryable());
        assertTrue(assertThrows(FeishuAuthException.class, provider::tenantAccessToken).retryable());
    }
}
