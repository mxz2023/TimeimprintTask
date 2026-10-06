package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

/** 飞书 tenant_access_token 提供者；真实实现带缓存与过期刷新。 */
public interface FeishuTokenProvider {

    /**
     * 返回当前可用的 tenant_access_token；调用方不得记录其明文。
     *
     * @throws FeishuAuthException 取令牌失败（网络、凭据被拒、限流等）
     */
    String tenantAccessToken();

    /** 令牌被飞书判定失效时丢弃缓存，下次调用重新获取；默认无缓存实现可忽略。 */
    default void invalidate() {}
}
