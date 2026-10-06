package cn.net.mxz.timeimprint.task.adapter.feishu.client;

import cn.net.mxz.timeimprint.task.adapter.feishu.auth.CachingFeishuTokenProvider;
import cn.net.mxz.timeimprint.task.adapter.feishu.auth.FeishuAuthException;
import cn.net.mxz.timeimprint.task.adapter.feishu.auth.FeishuTokenProvider;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuApiException.Category;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuAdapterProperties;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuCredentials;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 基于 JDK {@link HttpClient} 的飞书出站实现：
 * {@code POST /open-apis/im/v1/messages?receive_id_type=open_id}，{@code msg_type=interactive}。
 *
 * <p>单次调用只发一个 HTTP 请求（不做隐藏重试）；失败按 {@link Category} 分类，由上层映射到 Action 结果。
 */
public class HttpFeishuMessageClient implements FeishuMessageClient {

    static final String MESSAGES_PATH = "/open-apis/im/v1/messages?receive_id_type=open_id";
    public static final int MAX_UUID_LENGTH = 50;

    /** 令牌无效/过期：丢弃缓存后可重试。 */
    private static final Set<Integer> TOKEN_INVALID_CODES = Set.of(99991661, 99991663, 99991664, 99991668);
    /** 限流：可稍后重试。 */
    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(99991400, 230020);

    private final FeishuAdapterProperties properties;
    private final FeishuTokenProvider tokenProvider;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;

    public HttpFeishuMessageClient(
            FeishuAdapterProperties properties,
            FeishuTokenProvider tokenProvider,
            HttpClient httpClient,
            JsonMapper jsonMapper) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 组装「令牌缓存 + 发消息」的默认出站客户端。JDK {@link HttpClient} 只在 adapter 内创建，
     * 业务模块（notification）无需也不得直接依赖 {@code java.net.http}。
     * 平台自行管理重试（Action 状态机）：HttpClient 无隐藏重试，只设置连接超时。
     */
    public static HttpFeishuMessageClient create(
            FeishuAdapterProperties properties, FeishuCredentials credentials, JsonMapper jsonMapper, Clock clock) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
        var tokens = new CachingFeishuTokenProvider(properties, credentials, http, jsonMapper, clock);
        return new HttpFeishuMessageClient(properties, tokens, http, jsonMapper);
    }

    @Override
    public String sendInteractiveCard(String receiveOpenId, String cardJson, String uuid) {
        if (receiveOpenId == null || receiveOpenId.isBlank()) {
            throw new IllegalArgumentException("receiveOpenId must not be blank");
        }
        if (cardJson == null || cardJson.isBlank()) {
            throw new IllegalArgumentException("cardJson must not be blank");
        }
        if (uuid == null || uuid.isBlank() || uuid.length() > MAX_UUID_LENGTH) {
            throw new IllegalArgumentException("uuid must be 1.." + MAX_UUID_LENGTH + " chars");
        }

        String token;
        try {
            token = tokenProvider.tenantAccessToken();
        } catch (FeishuAuthException e) {
            // 令牌阶段尚未发出消息，因此可重试的失败仍是 RETRYABLE，而不是 UNKNOWN。
            throw new FeishuApiException(
                    e.retryable() ? Category.RETRYABLE : Category.PERMANENT, e.apiCode(), e.getMessage(), e);
        }

        ObjectNode body = jsonMapper.createObjectNode();
        body.put("receive_id", receiveOpenId);
        body.put("msg_type", "interactive");
        body.put("content", cardJson);
        body.put("uuid", uuid);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.normalizedBaseUrl() + MESSAGES_PATH))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new FeishuApiException(Category.RETRYABLE, -1, "connect failed: " + e.getClass().getSimpleName(), e);
        } catch (IOException e) {
            // 请求可能已送达飞书，读超时/连接中断时结果不明。
            throw new FeishuApiException(Category.UNKNOWN, -1, "send outcome unknown: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException(Category.UNKNOWN, -1, "send interrupted", e);
        }
        return interpret(response);
    }

    private String interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new FeishuApiException(Category.RETRYABLE, -1, "feishu http " + status, null);
        }
        JsonNode json = parse(response.body());
        if (json == null) {
            Category category = status >= 200 && status < 300 ? Category.UNKNOWN : Category.PERMANENT;
            throw new FeishuApiException(category, -1, "feishu http " + status + " unparsable body", null);
        }
        int code = json.path("code").asInt(-1);
        if (code == 0) {
            String messageId = json.path("data").path("message_id").asString("");
            if (messageId.isBlank()) {
                throw new FeishuApiException(Category.UNKNOWN, 0, "feishu accepted without message_id", null);
            }
            return messageId;
        }
        String msg = "feishu code=" + code + " msg=" + json.path("msg").asString("");
        if (TOKEN_INVALID_CODES.contains(code)) {
            tokenProvider.invalidate();
            throw new FeishuApiException(Category.RETRYABLE, code, msg, null);
        }
        if (RATE_LIMIT_CODES.contains(code)) {
            throw new FeishuApiException(Category.RETRYABLE, code, msg, null);
        }
        throw new FeishuApiException(Category.PERMANENT, code, msg, null);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(body);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
