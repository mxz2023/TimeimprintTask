package cn.net.mxz.timeimprint.task.adapter.feishu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.feishu.auth.CachingFeishuTokenProvider;
import cn.net.mxz.timeimprint.task.adapter.feishu.auth.FeishuAuthException;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuApiException.Category;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuAdapterProperties;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuCredentials;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F03/F04：Mock 飞书上的 interactive 发信、uuid 幂等键、message_id 受理号与失败分类。 */
class HttpFeishuMessageClientTest {

    private static final String OK_TOKEN = "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-abc\",\"expire\":7200}";
    private static final String CARD = "{\"schema\":\"2.0\"}";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private FeishuMockServerFixture server;
    private HttpFeishuMessageClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FeishuMockServerFixture();
        client = clientFor(server.baseUrl(), 2);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpFeishuMessageClient clientFor(String baseUrl, int timeoutSeconds) {
        FeishuAdapterProperties props = new FeishuAdapterProperties(baseUrl, timeoutSeconds);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        var tokens = new CachingFeishuTokenProvider(
                props, new FeishuCredentials("cli_app", "secret_x"), http, mapper, Clock.systemUTC());
        return new HttpFeishuMessageClient(props, tokens, http, mapper);
    }

    @Test
    void sendsInteractiveCardWithUuidAndReturnsMessageId() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(
                FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":0,\"msg\":\"success\",\"data\":{\"message_id\":\"om_1\"}}");

        String messageId = client.sendInteractiveCard("ou_1", CARD, "uuid-1");

        assertEquals("om_1", messageId);
        var sent = server.requestsTo(FeishuMockServerFixture.MESSAGES_PATH).getFirst();
        assertEquals("POST", sent.method());
        assertEquals("receive_id_type=open_id", sent.query());
        assertEquals("Bearer t-abc", sent.headers().get("authorization"));
        JsonNode body = mapper.readTree(sent.body());
        assertEquals("ou_1", body.path("receive_id").asString());
        assertEquals("interactive", body.path("msg_type").asString());
        assertEquals(CARD, body.path("content").asString());
        assertEquals("uuid-1", body.path("uuid").asString());
        var tokenReq = server.requestsTo(FeishuMockServerFixture.TOKEN_PATH).getFirst();
        JsonNode tokenBody = mapper.readTree(tokenReq.body());
        assertEquals("cli_app", tokenBody.path("app_id").asString());
        assertEquals("secret_x", tokenBody.path("app_secret").asString());
    }

    @Test
    void createWiresTokenProviderAndHttpClientInsideAdapter() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(
                FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":0,\"msg\":\"success\",\"data\":{\"message_id\":\"om_f\"}}");
        HttpFeishuMessageClient created = HttpFeishuMessageClient.create(
                new FeishuAdapterProperties(server.baseUrl(), 2),
                new FeishuCredentials("cli_app", "secret_x"),
                mapper,
                Clock.systemUTC());

        assertEquals("om_f", created.sendInteractiveCard("ou_1", CARD, "uuid-f"));
        assertEquals(1, server.requestsTo(FeishuMockServerFixture.TOKEN_PATH).size());
        assertEquals(
                "Bearer t-abc",
                server.requestsTo(FeishuMockServerFixture.MESSAGES_PATH).getFirst().headers().get("authorization"));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> client.sendInteractiveCard(" ", CARD, "u"));
        assertThrows(IllegalArgumentException.class, () -> client.sendInteractiveCard("ou", "", "u"));
        assertThrows(IllegalArgumentException.class, () -> client.sendInteractiveCard("ou", CARD, "x".repeat(51)));
        assertEquals(0, server.requests().size());
    }

    @Test
    void businessErrorIsPermanent() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 400, "{\"code\":230013,\"msg\":\"no permission\"}");
        FeishuApiException e =
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u"));
        assertEquals(Category.PERMANENT, e.category());
        assertEquals(230013, e.apiCode());
    }

    @Test
    void serverErrorAndRateLimitAreRetryable() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 503, "");
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":99991400,\"msg\":\"rate\"}");
        assertEquals(
                Category.RETRYABLE,
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u")).category());
        assertEquals(
                Category.RETRYABLE,
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u")).category());
        // 单次调用只发一个消息请求，无隐藏重试；令牌被缓存只取一次。
        assertEquals(2, server.requestsTo(FeishuMockServerFixture.MESSAGES_PATH).size());
        assertEquals(1, server.requestsTo(FeishuMockServerFixture.TOKEN_PATH).size());
    }

    @Test
    void tokenInvalidCodeDropsCachedTokenAndIsRetryable() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN.replace("t-abc", "t-new"));
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":99991663,\"msg\":\"invalid\"}");
        server.enqueue(
                FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":0,\"msg\":\"success\",\"data\":{\"message_id\":\"om_2\"}}");

        assertEquals(
                Category.RETRYABLE,
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u")).category());
        assertEquals("om_2", client.sendInteractiveCard("ou_1", CARD, "u"));
        var sends = server.requestsTo(FeishuMockServerFixture.MESSAGES_PATH);
        assertEquals("Bearer t-new", sends.get(1).headers().get("authorization"));
    }

    @Test
    void acceptedWithoutMessageIdIsUnknown() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 200, "{\"code\":0,\"msg\":\"success\",\"data\":{}}");
        FeishuApiException e =
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u"));
        assertEquals(Category.UNKNOWN, e.category());
    }

    @Test
    void unparsableBodyDependsOnStatus() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 200, "not-json");
        server.enqueue(FeishuMockServerFixture.MESSAGES_PATH, 400, "not-json");
        assertEquals(
                Category.UNKNOWN,
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u")).category());
        assertEquals(
                Category.PERMANENT,
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u")).category());
    }

    @Test
    void readTimeoutAfterSendIsUnknown() throws IOException {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 200, OK_TOKEN);
        server.enqueue(
                FeishuMockServerFixture.MESSAGES_PATH,
                new FeishuMockServerFixture.Reply(
                        200, "{\"code\":0,\"data\":{\"message_id\":\"om_late\"}}", 3_000));
        HttpFeishuMessageClient fast = clientFor(server.baseUrl(), 1);
        FeishuApiException e =
                assertThrows(FeishuApiException.class, () -> fast.sendInteractiveCard("ou_1", CARD, "u"));
        assertEquals(Category.UNKNOWN, e.category());
    }

    @Test
    void connectionRefusedIsRetryable() throws IOException {
        String deadUrl;
        try (var dead = new FeishuMockServerFixture()) {
            deadUrl = dead.baseUrl();
        }
        HttpFeishuMessageClient offline = clientFor(deadUrl, 1);
        // 令牌阶段即连接失败：消息尚未发出，可重试。
        FeishuApiException e =
                assertThrows(FeishuApiException.class, () -> offline.sendInteractiveCard("ou_1", CARD, "u"));
        assertEquals(Category.RETRYABLE, e.category());
    }

    @Test
    void tokenRejectionIsPermanentAndMessageNotSent() {
        server.enqueue(FeishuMockServerFixture.TOKEN_PATH, 400, "{\"code\":10003,\"msg\":\"invalid param\"}");
        FeishuApiException e =
                assertThrows(FeishuApiException.class, () -> client.sendInteractiveCard("ou_1", CARD, "u"));
        assertEquals(Category.PERMANENT, e.category());
        assertEquals(10003, e.apiCode());
        assertTrue(e.getCause() instanceof FeishuAuthException);
        assertEquals(0, server.requestsTo(FeishuMockServerFixture.MESSAGES_PATH).size());
    }
}
