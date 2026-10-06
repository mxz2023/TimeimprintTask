package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler;

import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuApiException;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuMessageClient;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.adapter.FeishuCardBuilder;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration.FeishuNotificationProperties;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.ActionHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.ActionHandler;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * EXTERNAL 飞书 IM 通知 Handler，handler_key = "feishu_im_notification"，schema_version = 1。
 *
 * <p>出站流程（P05 T03）：调用方 {@code ActionExternalExecutor} 已先写入 {@code effectStartedAt}，本类在事务外
 * 经 adapter 的 {@link FeishuMessageClient} 发送 {@code msg_type=interactive} 卡片；{@code uuid} 由 actionKey
 * 摘要导出（≤50），成功时把飞书受理号 {@code message_id} 记入 {@code safeSummary}。
 *
 * <p>失败分类：缺 recipient-map / 卡片载荷缺字段 / 飞书明确拒绝 → PERMANENT_FAILURE；连接失败、限流、5xx、令牌失效
 * → RETRYABLE_FAILURE；超时或结果不明 → UNKNOWN。默认 delivery-channels 仅 IN_APP 时不会产生该 Action。
 */
@Component
public class FeishuImNotificationHandler implements ActionHandler {

    public static final String HANDLER_KEY = "feishu_im_notification";
    public static final int SCHEMA_VERSION = 1;
    /** payload 键：EXTERNAL 执行器注入的发信时实例 revision。 */
    public static final String REVISION_FIELD = "instanceRevision";

    static final int MAX_SUMMARY_LENGTH = 300;

    private final Optional<FeishuMessageClient> client;
    private final FeishuNotificationProperties properties;
    private final FeishuCardBuilder cardBuilder = new FeishuCardBuilder();

    /** @param client 仅在 FEISHU 启用时存在；缺失时执行返回 PERMANENT_FAILURE。 */
    public FeishuImNotificationHandler(Optional<FeishuMessageClient> client, FeishuNotificationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

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
        return FeishuNotificationProperties.OUTBOUND_BUDGET_SECONDS;
    }

    @Override
    public Set<Integer> supportedSchemaVersions() {
        return Set.of(SCHEMA_VERSION);
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        if (client.isEmpty()) {
            return failure(ActionHandlerOutcome.PERMANENT_FAILURE, "FEISHU_NOT_CONFIGURED", "feishu client not assembled");
        }
        Map<String, Object> fields = context.payload() instanceof JsonPayload jp ? jp.fields() : Map.of();

        String recipientId = text(fields.get("recipientId"));
        String openId = properties.openIdFor(recipientId);
        if (openId == null) {
            return failure(
                    ActionHandlerOutcome.PERMANENT_FAILURE,
                    "FEISHU_RECIPIENT_UNMAPPED",
                    "no recipient-map entry for recipient");
        }

        String cardJson;
        try {
            cardJson = buildCard(context, fields);
        } catch (IllegalArgumentException e) {
            return failure(ActionHandlerOutcome.PERMANENT_FAILURE, "FEISHU_CARD_INVALID", e.getMessage());
        }

        try {
            String messageId = client.get().sendInteractiveCard(openId, cardJson, uuidFor(context.actionKey()));
            return new ActionExecutionResult(
                    ActionHandlerOutcome.SUCCEEDED, "FEISHU_MESSAGE_SENT", "message_id=" + messageId);
        } catch (FeishuApiException e) {
            return switch (e.category()) {
                case RETRYABLE -> failure(ActionHandlerOutcome.RETRYABLE_FAILURE, "FEISHU_RETRYABLE", e.getMessage());
                case PERMANENT -> failure(ActionHandlerOutcome.PERMANENT_FAILURE, "FEISHU_REJECTED", e.getMessage());
                case UNKNOWN -> failure(ActionHandlerOutcome.UNKNOWN, "FEISHU_UNKNOWN", e.getMessage());
            };
        } catch (RuntimeException e) {
            // effectStartedAt 已落库，意外异常时无法证明未送达，保守归 UNKNOWN。
            return failure(
                    ActionHandlerOutcome.UNKNOWN, "FEISHU_UNEXPECTED", e.getClass().getSimpleName());
        }
    }

    private String buildCard(ActionExecutionContext context, Map<String, Object> fields) {
        String scenarioKey = text(fields.get("scenarioKey"));
        String title = text(fields.get("title"));
        String body = text(fields.get("body"));
        if (!FeishuCardBuilder.hasActions(scenarioKey)) {
            return cardBuilder.build(title, body, false, 0, 0, 0);
        }
        if (context.instanceId() == null) {
            throw new IllegalArgumentException("actionable card requires instanceId");
        }
        if (!(fields.get(REVISION_FIELD) instanceof Number revision)) {
            throw new IllegalArgumentException("actionable card requires " + REVISION_FIELD);
        }
        return cardBuilder.build(
                title, body, true, context.definitionId(), context.instanceId(), revision.longValue());
    }

    /** 飞书 uuid 上限 50：用 actionKey 的 SHA-256 做 URL-safe Base64（43 字符），稳定且不泄露内部键。 */
    static String uuidFor(String actionKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Sha256.digestUtf8(actionKey));
    }

    private static ActionExecutionResult failure(ActionHandlerOutcome outcome, String code, String summary) {
        String s = summary == null ? null : summary.length() > MAX_SUMMARY_LENGTH ? summary.substring(0, MAX_SUMMARY_LENGTH) : summary;
        return new ActionExecutionResult(outcome, code, s);
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
