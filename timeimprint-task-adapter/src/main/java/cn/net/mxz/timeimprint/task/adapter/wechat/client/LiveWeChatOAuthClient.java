package cn.net.mxz.timeimprint.task.adapter.wechat.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** live 模式：用 code 向微信开放平台换 openid；访问令牌不落库、不进日志与异常。 */
public class LiveWeChatOAuthClient implements WeChatOAuthClient {

    private static final String INVALID = "微信授权无效";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @FunctionalInterface
    interface HttpGet {
        String get(String url) throws IOException, InterruptedException;
    }

    private final String appId;
    private final String appSecret;
    private final HttpGet httpGet;

    public LiveWeChatOAuthClient(String appId, String appSecret) {
        this(appId, appSecret, defaultHttpGet());
    }

    LiveWeChatOAuthClient(String appId, String appSecret, HttpGet httpGet) {
        this.appId = require(appId, "app-id");
        this.appSecret = require(appSecret, "app-secret");
        this.httpGet = httpGet;
    }

    @Override
    public WeChatProfile exchange(String appType, String code, String state) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException(INVALID);
        }
        String type = appType == null || appType.isBlank() ? "web" : appType.trim();
        JsonNode token = parseJson(fetchToken(code.trim()), INVALID);
        if (token.hasNonNull("errcode") && token.get("errcode").asInt() != 0) {
            throw new IllegalStateException(INVALID);
        }
        String openid = text(token, "openid");
        if (openid.isBlank()) {
            throw new IllegalStateException(INVALID);
        }
        String accessToken = text(token, "access_token");
        String unionid = text(token, "unionid");
        String nickname = "微信用户";
        String avatar = "";
        if (!accessToken.isBlank()) {
            JsonNode profile = tryUserInfo(accessToken, openid);
            if (profile != null) {
                if (!text(profile, "nickname").isBlank()) {
                    nickname = text(profile, "nickname");
                }
                avatar = text(profile, "headimgurl");
                if (unionid.isBlank()) {
                    unionid = text(profile, "unionid");
                }
            }
        }
        return new WeChatProfile(type, openid, unionid, nickname, avatar);
    }

    public static String buildQrConnectAuthUrl(String appId, String redirectUri, String state) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return "https://open.weixin.qq.com/connect/qrconnect?appid="
                + appId
                + "&redirect_uri="
                + encodedRedirect
                + "&response_type=code&scope=snsapi_login&state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "#wechat_redirect";
    }

    private String fetchToken(String code) {
        try {
            String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid="
                    + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&secret="
                    + URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
                    + "&code="
                    + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            return httpGet.get(url);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(INVALID);
        } catch (IOException ex) {
            throw new IllegalStateException(INVALID);
        }
    }

    private JsonNode tryUserInfo(String accessToken, String openid) {
        try {
            String url = "https://api.weixin.qq.com/sns/userinfo?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                    + "&openid="
                    + URLEncoder.encode(openid, StandardCharsets.UTF_8)
                    + "&lang=zh_CN";
            JsonNode body = parseJson(httpGet.get(url), INVALID);
            if (body.hasNonNull("errcode") && body.get("errcode").asInt() != 0) {
                return null;
            }
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static JsonNode parseJson(String body, String invalidMessage) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException(invalidMessage);
        }
        try {
            return JSON.readTree(body);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(invalidMessage);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("timeimprint.identity.wechat.web." + name + " is required for live mode");
        }
        return value.trim();
    }

    private static HttpGet defaultHttpGet() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return url -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("http " + response.statusCode());
            }
            return response.body();
        };
    }
}
