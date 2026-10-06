package cn.net.mxz.timeimprint.task.adapter.feishu.configuration;

/**
 * 飞书适配器与传输相关的纯值配置（不含凭据；凭据见 {@link FeishuCredentials}）。
 *
 * @param baseUrl 飞书开放平台基地址（末尾斜杠会被忽略）
 * @param timeoutSeconds 单次 HTTP 调用超时秒数（一次出站最多含「取令牌 + 发消息」两次调用）
 */
public record FeishuAdapterProperties(String baseUrl, int timeoutSeconds) {

    public static final String DEFAULT_BASE_URL = "https://open.feishu.cn";
    public static final int DEFAULT_TIMEOUT_SECONDS = 5;

    public FeishuAdapterProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }

    public static FeishuAdapterProperties defaults() {
        return new FeishuAdapterProperties(DEFAULT_BASE_URL, DEFAULT_TIMEOUT_SECONDS);
    }

    /** 去掉末尾斜杠后的基地址，便于拼接绝对路径。 */
    public String normalizedBaseUrl() {
        String b = baseUrl.strip();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }
}
