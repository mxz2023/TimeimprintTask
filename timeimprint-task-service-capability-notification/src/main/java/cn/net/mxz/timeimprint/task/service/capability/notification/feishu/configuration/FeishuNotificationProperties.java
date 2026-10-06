package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration;

import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuAdapterProperties;
import cn.net.mxz.timeimprint.task.adapter.feishu.configuration.FeishuCredentials;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 {@code timeimprint.notification.feishu.*}（03 §3.1）。
 *
 * <p>仅当 delivery-channels 含 FEISHU 时才会被 {@link #validateForOutbound(int)} 校验；默认仅 IN_APP 时不要求任何飞书配置。
 * 凭据只来自运行配置，禁止写入仓库；本类不实现 toString，避免日志泄露密钥。
 */
@Component
@ConfigurationProperties(prefix = "timeimprint.notification.feishu")
public class FeishuNotificationProperties {

    /** 飞书 Handler 声明的 timeoutSeconds 预算；一次出站最多「取令牌 + 发消息」两次 HTTP 调用。 */
    public static final int OUTBOUND_BUDGET_SECONDS = 10;

    private String appId;
    private String appSecret;
    /** T04 入站回调使用；出站不要求。 */
    private String verificationToken;
    /** T04 入站回调使用；可选。 */
    private String encryptKey;
    private String baseUrl = FeishuAdapterProperties.DEFAULT_BASE_URL;
    private int timeoutSeconds = FeishuAdapterProperties.DEFAULT_TIMEOUT_SECONDS;
    private Map<String, String> recipientMap = new LinkedHashMap<>();

    /**
     * 启用 FEISHU 出站时的启动校验；失败抛出 {@link IllegalStateException}。
     * recipient-map 允许为空或部分缺失：缺映射在执行期归为 PERMANENT_FAILURE，而不是启动失败。
     */
    public void validateForOutbound(int handlerBudgetSeconds) {
        if (isBlank(appId)) {
            throw new IllegalStateException("timeimprint.notification.feishu.app-id is required when FEISHU is enabled");
        }
        if (isBlank(appSecret)) {
            throw new IllegalStateException(
                    "timeimprint.notification.feishu.app-secret is required when FEISHU is enabled");
        }
        if (isBlank(baseUrl)) {
            throw new IllegalStateException("timeimprint.notification.feishu.base-url must not be blank");
        }
        try {
            URI uri = URI.create(baseUrl.strip());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalStateException("timeimprint.notification.feishu.base-url must be an http(s) URL");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("timeimprint.notification.feishu.base-url is not a valid URL", e);
        }
        if (timeoutSeconds < 1 || (long) timeoutSeconds * 2 > handlerBudgetSeconds) {
            throw new IllegalStateException("timeimprint.notification.feishu.timeout-seconds must be in 1.."
                    + (handlerBudgetSeconds / 2) + " (token + send must fit the handler timeout of "
                    + handlerBudgetSeconds + "s)");
        }
        for (Map.Entry<String, String> e : recipientMap.entrySet()) {
            if (isBlank(e.getKey()) || isBlank(e.getValue())) {
                throw new IllegalStateException("timeimprint.notification.feishu.recipient-map has blank entry");
            }
        }
    }

    public FeishuAdapterProperties adapterProperties() {
        return new FeishuAdapterProperties(baseUrl, timeoutSeconds);
    }

    public FeishuCredentials credentials() {
        return new FeishuCredentials(appId, appSecret);
    }

    /** 平台 recipientId → 飞书 open_id；缺映射返回 null。 */
    public String openIdFor(String recipientId) {
        if (recipientId == null) {
            return null;
        }
        String v = recipientMap.get(recipientId);
        return isBlank(v) ? null : v.strip();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Map<String, String> getRecipientMap() {
        return recipientMap;
    }

    public void setRecipientMap(Map<String, String> recipientMap) {
        this.recipientMap = recipientMap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(recipientMap);
    }
}
