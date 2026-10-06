package cn.net.mxz.timeimprint.task.adapter.feishu.client;

/** 飞书出站调用失败，带可观察的失败分类。消息不得包含令牌或密钥。 */
public class FeishuApiException extends RuntimeException {

    /** 失败分类：对应平台 Action 的重试/终止/未知语义。 */
    public enum Category {
        /** 请求未被受理（连接失败、限流、5xx、令牌过期等），uuid 幂等下可安全重试。 */
        RETRYABLE,
        /** 请求被明确拒绝且重试无意义（参数、权限、凭据等）。 */
        PERMANENT,
        /** 请求可能已被受理但结果不明（读超时、连接中断、2xx 无 message_id）。 */
        UNKNOWN
    }

    private final Category category;
    private final int apiCode;

    /**
     * @param apiCode 飞书业务码；无则 -1
     */
    public FeishuApiException(Category category, int apiCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.apiCode = apiCode;
    }

    public Category category() {
        return category;
    }

    public int apiCode() {
        return apiCode;
    }
}
