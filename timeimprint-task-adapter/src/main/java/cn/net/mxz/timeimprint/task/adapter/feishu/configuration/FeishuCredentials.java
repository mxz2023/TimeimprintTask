package cn.net.mxz.timeimprint.task.adapter.feishu.configuration;

/**
 * 飞书应用凭据（app-id / app-secret）。凭据只来自运行配置，禁止写入仓库；{@link #toString()} 不输出密钥。
 *
 * @param appId 飞书应用 App ID
 * @param appSecret 飞书应用 App Secret
 */
public record FeishuCredentials(String appId, String appSecret) {

    public FeishuCredentials {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("feishu app-id must not be blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("feishu app-secret must not be blank");
        }
    }

    @Override
    public String toString() {
        return "FeishuCredentials[appId=" + appId + ", appSecret=***]";
    }
}
