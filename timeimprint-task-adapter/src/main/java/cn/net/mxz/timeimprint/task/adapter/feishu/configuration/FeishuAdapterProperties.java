package cn.net.mxz.timeimprint.task.adapter.feishu.configuration;

/**
 * 飞书适配器与传输相关的纯值配置（不含凭据；凭据绑定与启用校验由 T03 完成）。
 *
 * @param baseUrl 飞书开放平台基地址
 * @param timeoutSeconds 单次 HTTP 调用超时秒数
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
}
