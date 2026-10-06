package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

/** 飞书卡片回调（card.action.trigger）验签（P05 T02 仅为契约桩；真实校验由 T04 实现）。 */
public interface FeishuCardActionVerifier {

    /**
     * @param timestamp 请求头时间戳
     * @param nonce 请求头随机串
     * @param signature 请求头签名
     * @param body 原始请求体字节
     * @return 校验通过返回 true；任何不确定情形必须返回 false
     */
    boolean verify(String timestamp, String nonce, String signature, byte[] body);
}
