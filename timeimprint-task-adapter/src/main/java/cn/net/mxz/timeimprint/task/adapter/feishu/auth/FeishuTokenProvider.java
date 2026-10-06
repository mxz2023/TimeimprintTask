package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

/** 飞书 tenant_access_token 提供者（P05 T02 仅为契约桩；缓存与刷新由 T03 实现）。 */
public interface FeishuTokenProvider {

    /** 返回当前可用的 tenant_access_token；调用方不得记录其明文。 */
    String tenantAccessToken();
}
