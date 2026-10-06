package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuAdapterProperties;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 通过 {@code POST /open-apis/auth/v3/tenant_access_token/internal} 获取并缓存 tenant_access_token。
 *
 * <p>缓存到「过期前 {@link #REFRESH_MARGIN}」；单次调用只发一个 HTTP 请求，不做隐藏重试。
 */
public class CachingFeishuTokenProvider implements FeishuTokenProvider {

    static final String TOKEN_PATH = "/open-apis/auth/v3/tenant_access_token/internal";
    static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    private static final int RATE_LIMIT_CODE = 99991400;

    private final FeishuAdapterProperties properties;
    private final FeishuCredentials credentials;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public CachingFeishuTokenProvider(
            FeishuAdapterProperties properties,
            FeishuCredentials credentials,
            HttpClient httpClient,
            JsonMapper jsonMapper,
            Clock clock) {
        this.properties = properties;
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public synchronized String tenantAccessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedUntil)) {
            return cachedToken;
        }
        fetch(now);
        return cachedToken;
    }

    @Override
    public synchronized void invalidate() {
        cachedToken = null;
        cachedUntil = Instant.EPOCH;
    }

    private void fetch(Instant now) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("app_id", credentials.appId());
        body.put("app_secret", credentials.appSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.normalizedBaseUrl() + TOKEN_PATH))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FeishuAuthException(true, -1, "tenant_access_token request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeishuAuthException(true, -1, "tenant_access_token request interrupted", e);
        }
        int status = response.statusCode();
        JsonNode json = parse(response.body());
        if (json == null) {
            boolean retry = status >= 500 || status == 429;
            throw new FeishuAuthException(retry, -1, "tenant_access_token http " + status + " unparsable body", null);
        }
        int code = json.path("code").asInt(-1);
        String token = json.path("tenant_access_token").asString("");
        if (code != 0 || token.isBlank()) {
            boolean retry = status >= 500 || status == 429 || code == RATE_LIMIT_CODE;
            throw new FeishuAuthException(
                    retry, code, "tenant_access_token rejected: code=" + code + " msg=" + json.path("msg").asString(""), null);
        }
        long expireSeconds = json.path("expire").asLong(0);
        Instant until = now.plusSeconds(expireSeconds).minus(REFRESH_MARGIN);
        cachedToken = token;
        cachedUntil = until;
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
