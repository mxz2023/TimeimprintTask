package cn.net.mxz.timeimprint.task.adapter.feishu.client;

/**
 * 飞书出站消息客户端（P05 T02 仅为契约桩；真实 HTTP 实现由 T03 提供）。
 *
 * <p>实现必须在一次调用的超时预算内完成，并关闭 SDK 隐藏重试；{@code uuid} 由平台 actionKey 导出（≤50）。
 */
public interface FeishuMessageClient {

    /**
     * 向指定 open_id 发送交互卡片。
     *
     * @param receiveOpenId 飞书 open_id
     * @param cardJson 卡片 JSON 内容
     * @param uuid 幂等键（≤50）
     * @return 飞书受理号 message_id
     */
    String sendInteractiveCard(String receiveOpenId, String cardJson, String uuid);
}
