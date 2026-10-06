package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler;

import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * EXTERNAL 飞书 IM 通知 Handler，handler_key = "feishu_im_notification"，schema_version = 1。
 *
 * <p>P05 T02 仅占位：尚未接入 adapter 的真实飞书客户端，执行一律返回 PERMANENT_FAILURE
 * （{@code FEISHU_NOT_IMPLEMENTED}），避免伪造受理号；真实出站由 T03 实现。
 * 默认 delivery-channels 仅 IN_APP 时不会产生该 Action。
 */
@Component
public class FeishuImNotificationHandler implements ActionHandler {

    public static final String HANDLER_KEY = "feishu_im_notification";
    public static final int SCHEMA_VERSION = 1;

    @Override
    public ActionHandlerKey registrationKey() {
        return new ActionHandlerKey(HANDLER_KEY, SCHEMA_VERSION);
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.EXTERNAL;
    }

    @Override
    public int timeoutSeconds() {
        return 10;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(SCHEMA_VERSION);
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        return new ActionExecutionResult(
                ActionHandlerOutcome.PERMANENT_FAILURE,
                "FEISHU_NOT_IMPLEMENTED",
                "feishu outbound not implemented until P05 T03");
    }
}
