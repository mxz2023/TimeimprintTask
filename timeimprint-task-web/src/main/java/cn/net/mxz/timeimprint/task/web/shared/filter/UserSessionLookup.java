package cn.net.mxz.timeimprint.task.web.shared.filter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 任务接口的登录令牌只向用户服务核对，不查询任务库里的会话表。
 */
@Component
public class UserSessionLookup {

    private final String baseUrl;
    private final String serviceToken;
    private final Duration timeout;
    private final HttpClient http;
    private final boolean fixtureTokens;

    public UserSessionLookup(
            @Value("${timeimprint.user.base-url:}") String baseUrl,
            @Value("${timeimprint.user.service-token:}") String serviceToken,
            @Value("${timeimprint.user.read-timeout-ms:2000}") long readTimeoutMs,
            @Value("${timeimprint.user.fixture-tokens:false}") boolean fixtureTokens) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        long bounded = readTimeoutMs < 1 ? 2000 : readTimeoutMs;
        this.timeout = Duration.ofMillis(bounded);
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.fixtureTokens = fixtureTokens;
    }

    public Optional<RemoteSession> find(String rawToken) {
        if (fixtureTokens) {
            return fixtureSession(rawToken);
        }
        if (baseUrl.isBlank() || serviceToken.isBlank()) {
            throw new UserDirectoryUnavailable("未配置用户服务地址");
        }
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(root + "/internal/v1/sessions/introspect"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + rawToken)
                .header("X-TimeImprint-Service-Token", serviceToken)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserDirectoryUnavailable("用户服务会话核对被中断");
        } catch (Exception ex) {
            throw new UserDirectoryUnavailable("用户服务暂时不可用");
        }
        if (response.statusCode() == 401) {
            return Optional.empty();
        }
        if (response.statusCode() == 403) {
            throw new UserDirectoryUnavailable("用户服务拒绝了服务令牌");
        }
        if (response.statusCode() != 200 || response.body() == null || !ok(response.body())) {
            throw new UserDirectoryUnavailable("用户服务会话核对失败");
        }
        String tenant = jsonString(response.body(), "tenantKey");
        String actor = jsonString(response.body(), "actorKey");
        if (tenant == null || actor == null || tenant.isBlank() || actor.isBlank()) {
            throw new UserDirectoryUnavailable("用户服务会话核对失败");
        }
        return Optional.of(new RemoteSession(tenant, actor));
    }

    /** 只在测试打开。生产配置不得打开，固定测试令牌不能绕过用户服务。 */
    static Optional<RemoteSession> fixtureSession(String rawToken) {
        return switch (rawToken) {
            case "it-token-local-actor" -> Optional.of(new RemoteSession("local-tenant", "local-actor"));
            case "it-token-test-actor" -> Optional.of(new RemoteSession("test-tenant", "test-actor"));
            case "it-token-actor-a" -> Optional.of(new RemoteSession("test-tenant", "actor-a"));
            case "it-token-actor-b" -> Optional.of(new RemoteSession("test-tenant", "actor-b"));
            default -> Optional.empty();
        };
    }

    static boolean ok(String json) {
        return json.contains("\"code\":\"OK\"") || json.contains("\"code\": \"OK\"");
    }

    static String jsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int at = json.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + key.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    public record RemoteSession(String tenantId, String actorKey) {}

    public static class UserDirectoryUnavailable extends RuntimeException {
        public UserDirectoryUnavailable(String message) {
            super(message);
        }
    }
}
