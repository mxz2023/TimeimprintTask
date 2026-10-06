package cn.net.mxz.timeimprint.task.service.extension.action.registry;

import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;

/**
 * 一个已启用的通知投递渠道：把渠道键映射到 Action 的 handlerKey 与执行模式。
 *
 * @param channelKey 稳定渠道键（如 IN_APP、FEISHU）
 * @param handlerKey 该渠道对应的 ActionHandler 键
 * @param executionMode 该渠道 Action 的执行模式
 * @param actionKeySegment 参与 actionKey 哈希正文的末段；IN_APP 沿用历史值 {@code in_app_notification}
 *     以保证既有 actionKey 不变，其余渠道使用 channelKey 本身
 */
public record DeliveryChannel(
        String channelKey, String handlerKey, ActionExecutionMode executionMode, String actionKeySegment) {

    public DeliveryChannel {
        if (channelKey == null || channelKey.isBlank()) {
            throw new IllegalArgumentException("channelKey required");
        }
        if (handlerKey == null || handlerKey.isBlank()) {
            throw new IllegalArgumentException("handlerKey required");
        }
        if (executionMode == null) {
            throw new IllegalArgumentException("executionMode required");
        }
        if (actionKeySegment == null || actionKeySegment.isBlank()) {
            throw new IllegalArgumentException("actionKeySegment required");
        }
    }
}
