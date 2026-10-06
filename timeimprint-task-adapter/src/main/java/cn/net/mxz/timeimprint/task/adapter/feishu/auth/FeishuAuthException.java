package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

/** 获取 tenant_access_token 失败。消息不得包含密钥或令牌。 */
public class FeishuAuthException extends RuntimeException {

    private final boolean retryable;
    private final int apiCode;

    /**
     * @param retryable 网络/限流/5xx 等可稍后重试为 true；凭据被拒等为 false
     * @param apiCode 飞书业务码；无则 -1
     */
    public FeishuAuthException(boolean retryable, int apiCode, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.apiCode = apiCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public int apiCode() {
        return apiCode;
    }
}
