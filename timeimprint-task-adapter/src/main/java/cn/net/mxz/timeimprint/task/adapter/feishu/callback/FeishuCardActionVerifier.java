package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

import java.util.Optional;

/** 飞书事件回调（含 card.action.trigger 与 url_verification）验签与解包。 */
public interface FeishuCardActionVerifier {

    /**
     * @param timestamp 请求头 {@code X-Lark-Request-Timestamp}
     * @param nonce 请求头 {@code X-Lark-Request-Nonce}
     * @param signature 请求头 {@code X-Lark-Signature}
     * @param body 原始请求体字节（不得先解析再重新序列化）
     * @return 校验通过返回 true；任何不确定情形必须返回 false
     */
    boolean verify(String timestamp, String nonce, String signature, byte[] body);

    /**
     * 取出回调明文 JSON：未加密时即请求体本身；开启加密时对 {@code {"encrypt":"..."}} 解密。
     *
     * @return 明文 JSON 文本；无法解析或无法解密时为空
     */
    Optional<String> openBody(byte[] body);
}
