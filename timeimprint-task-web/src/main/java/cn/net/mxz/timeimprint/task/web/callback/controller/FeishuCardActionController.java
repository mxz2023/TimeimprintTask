package cn.net.mxz.timeimprint.task.web.callback.controller;

import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuCardActionBridge;
import cn.net.mxz.timeimprint.task.web.callback.configuration.FeishuInboundEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 飞书卡片回调入口（P05 T04）：{@code POST /callbacks/v1/feishu/card-action}。
 *
 * <p>仅在飞书入站启用时注册（见 {@link FeishuInboundEnabledCondition}）。本类只做 HTTP 适配：取验签请求头和原始请求体，
 * 交给 {@link FeishuCardActionBridge}。应答是飞书回调格式（toast / challenge），<b>不是</b>平台 ApiResponse 信封，
 * 业务拒绝也返回 200，只有验签失败返回 401。
 */
@RestController
@Conditional(FeishuInboundEnabledCondition.class)
public class FeishuCardActionController {

    public static final String PATH = "/callbacks/v1/feishu/card-action";

    private final FeishuCardActionBridge bridge;

    public FeishuCardActionController(FeishuCardActionBridge bridge) {
        this.bridge = bridge;
    }

    @PostMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> cardAction(
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        FeishuCardActionBridge.Reply reply = bridge.handle(timestamp, nonce, signature, body);
        return ResponseEntity.status(reply.httpStatus()).body(reply.body());
    }
}
