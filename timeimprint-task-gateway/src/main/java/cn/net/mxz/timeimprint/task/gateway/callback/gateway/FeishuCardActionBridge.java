package cn.net.mxz.timeimprint.task.gateway.callback.gateway;

import cn.net.mxz.timeimprint.task.adapter.feishu.callback.FeishuCardActionVerifier;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.domain.instance.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.instance.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandMetadataView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.shared.view.Page;
import cn.net.mxz.timeimprint.task.domain.shared.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.shared.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 飞书 {@code card.action.trigger} 入站命令桥（P05 T04；P05 README §3.2、CAP03 NOT-05 入站）。
 *
 * <p>流程：验签 → 解包 → 解析 {@code action.value} → 反查 {@code operator.open_id} 并校验其为该实例参与人 →
 * 确认场景声明了该实例命令 → 映射为 complete / skip / snooze(+1h) → 调用与 HTTP E09 相同的
 * {@link TaskGateway#executeInstanceCommand}。命令仍以本地固定 Actor 执行，飞书身份只用于授权判断，不改写 Actor。
 *
 * <p>响应一律为飞书回调格式（{@code {"toast":{...}}}），而非平台 ApiResponse 信封；业务拒绝也返回 HTTP 200 + error toast，
 * 否则飞书不会展示提示。只有验签失败返回 HTTP 401。
 *
 * <p>幂等：{@code requestId} 由 {@code event_id} 确定性派生；snooze 的 {@code T} 优先取事件 {@code create_time}
 * （与当前时钟偏差不超过 {@link #MAX_EVENT_SKEW}），使同一事件重放得到相同 payload 哈希、命中平台命令去重而不重复生效。
 */
public final class FeishuCardActionBridge {

    public static final String EVENT_CARD_ACTION = "card.action.trigger";
    public static final String COMMAND_COMPLETE = "complete";
    public static final String COMMAND_SKIP = "skip";
    public static final String COMMAND_SNOOZE = "snooze";
    public static final String SKIP_REASON = "飞书卡片跳过";
    public static final Duration SNOOZE_DELTA = Duration.ofHours(1);
    /** 事件时间与当前时钟允许的最大偏差；超出则退回当前业务时钟。 */
    public static final Duration MAX_EVENT_SKEW = Duration.ofMinutes(5);

    private static final Set<String> COMMANDS = Set.of(COMMAND_COMPLETE, COMMAND_SKIP, COMMAND_SNOOZE);
    private static final Log LOG = LogFactory.getLog(FeishuCardActionBridge.class);

    /**
     * 对飞书的 HTTP 应答。
     *
     * @param httpStatus HTTP 状态码
     * @param body 应答 JSON
     */
    public record Reply(int httpStatus, JsonNode body) {}

    private final FeishuCardActionVerifier verifier;
    private final FeishuInboundSettings settings;
    private final TaskGateway gateway;
    private final BusinessClock clock;
    private final JsonMapper mapper;

    public FeishuCardActionBridge(
            FeishuCardActionVerifier verifier,
            FeishuInboundSettings settings,
            TaskGateway gateway,
            BusinessClock clock,
            JsonMapper mapper) {
        this.verifier = verifier;
        this.settings = settings;
        this.gateway = gateway;
        this.clock = clock;
        this.mapper = mapper;
    }

    /** 由 {@code event_id} 确定性派生符合 InstanceCommandRequest 正则的小写 UUID。 */
    public static String requestIdFor(String eventId) {
        return UUID.nameUUIDFromBytes(("feishu-card-action:" + eventId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 处理一次回调；任何异常都转为飞书可展示的 toast，不向上抛出。 */
    public Reply handle(String timestamp, String nonce, String signature, byte[] body) {
        if (!verifier.verify(timestamp, nonce, signature, body)) {
            ObjectNode denied = mapper.createObjectNode();
            denied.put("error", "invalid signature");
            return new Reply(401, denied);
        }
        JsonNode root;
        try {
            root = mapper.readTree(verifier.openBody(body).orElse(""));
        } catch (RuntimeException e) {
            return toast("error", "回调内容无法解析");
        }
        if ("url_verification".equals(text(root.path("type")))) {
            ObjectNode challenge = mapper.createObjectNode();
            challenge.put("challenge", text(root.path("challenge")) == null ? "" : text(root.path("challenge")));
            return new Reply(200, challenge);
        }
        JsonNode header = root.path("header");
        if (!EVENT_CARD_ACTION.equals(text(header.path("event_type")))) {
            // 其它事件（含 P05 不实现的文字回复）只确认收到，避免飞书重试。
            return new Reply(200, mapper.createObjectNode());
        }
        try {
            return handleCardAction(root);
        } catch (ApplicationException e) {
            return mapApplicationError(e);
        } catch (RuntimeException e) {
            LOG.error("feishu card action failed unexpectedly", e);
            return toast("error", "处理失败，请稍后重试");
        }
    }

    private Reply handleCardAction(JsonNode root) {
        String eventId = text(root.path("header").path("event_id"));
        if (eventId == null || eventId.isBlank()) {
            return toast("error", "回调缺少 event_id");
        }
        JsonNode event = root.path("event");
        String openId = text(event.path("operator").path("open_id"));
        JsonNode value = actionValue(event.path("action").path("value"));
        String commandKey = text(value.path("commandKey"));
        Long instanceId = longOf(value.path("instanceId"));
        Long definitionId = longOf(value.path("definitionId"));
        Long revision = longOf(value.path("revision"));
        if (commandKey == null
                || !COMMANDS.contains(commandKey)
                || instanceId == null
                || definitionId == null
                || revision == null
                || revision < 0) {
            return toast("error", "卡片参数无效，请刷新后重试");
        }

        Set<String> platformUsers = settings.platformUsersFor(openId);
        if (platformUsers.isEmpty()) {
            return toast("error", "你不是该任务的接收人，无法操作");
        }
        TaskInstanceView instance = gateway.getInstance(instanceId);
        if (!String.valueOf(definitionId).equals(instance.definitionId())) {
            return toast("error", "卡片与任务不匹配，请刷新后重试");
        }
        if (!isParticipant(instance, platformUsers)) {
            return toast("error", "你不是该任务的接收人，无法操作");
        }
        if (!scenarioSupports(instance.scenarioKey(), commandKey)) {
            return toast("error", "该任务不支持通过飞书卡片操作");
        }

        JsonNode payload = payloadFor(commandKey, eventTime(root.path("header").path("create_time")));
        var request = new InstanceCommandRequest(requestIdFor(eventId), revision, 1, payload);
        CommandResultView result = gateway.executeInstanceCommand(instanceId, commandKey, request);
        if (!result.changed()) {
            return toast("info", "该操作已处理，无需重复提交");
        }
        return toast("success", successMessage(commandKey));
    }

    private JsonNode payloadFor(String commandKey, Instant eventTime) {
        ObjectNode payload = mapper.createObjectNode();
        switch (commandKey) {
            case COMMAND_SKIP -> payload.put("reason", SKIP_REASON);
            case COMMAND_SNOOZE -> payload.put("snoozeUntil", eventTime.plus(SNOOZE_DELTA).toString());
            default -> {
                // complete：空载荷
            }
        }
        return payload;
    }

    /** snooze 的 T：优先事件 create_time（毫秒），否则或偏差过大时用业务时钟；均截断到整秒。 */
    private Instant eventTime(JsonNode createTime) {
        Instant now = clock.nowUtcSeconds();
        Long millis = longOf(createTime);
        if (millis == null) {
            return now;
        }
        Instant created = Instant.ofEpochMilli(millis).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return Duration.between(created, now).abs().compareTo(MAX_EVENT_SKEW) <= 0 ? created : now;
    }

    private boolean isParticipant(TaskInstanceView instance, Set<String> platformUsers) {
        if (instance.participants() == null) {
            return false;
        }
        for (ParticipantView p : instance.participants()) {
            if (p != null && p.principalId() != null && platformUsers.contains(p.principalId())) {
                return true;
            }
        }
        return false;
    }

    /** 以场景元数据判断是否声明了该实例命令（S01 无实例命令 → 拒绝）。 */
    private boolean scenarioSupports(String scenarioKey, String commandKey) {
        String cursor = null;
        do {
            Page<ScenarioMetadataView> page = gateway.listScenarios(cursor, 100);
            for (ScenarioMetadataView s : page.items()) {
                if (s.scenarioKey().equals(scenarioKey) && s.instanceCommands() != null) {
                    for (CommandMetadataView c : s.instanceCommands()) {
                        if (commandKey.equals(c.commandKey())) {
                            return true;
                        }
                    }
                }
            }
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return false;
    }

    private Reply mapApplicationError(ApplicationException e) {
        return switch (e.errorCode()) {
            case "REVISION_CONFLICT" -> toast("warning", "任务已有更新，卡片已过期，请在平台刷新后重试");
            case "STATE_CONFLICT", "INVALID_REQUEST" -> toast("warning", e.getMessage());
            case "IDEMPOTENCY_CONFLICT" -> toast("info", "该操作已处理，无需重复提交");
            case "RESOURCE_NOT_FOUND" -> toast("error", "任务不存在或已删除");
            case "RETRY_LATER" -> toast("warning", "系统繁忙，请稍后重试");
            default -> toast("error", "操作失败，请稍后重试");
        };
    }

    private static String successMessage(String commandKey) {
        return switch (commandKey) {
            case COMMAND_COMPLETE -> "已完成";
            case COMMAND_SKIP -> "已跳过";
            default -> "已稍后提醒（1 小时后）";
        };
    }

    /** 飞书 value 通常是对象；兼容被序列化成字符串的情形。 */
    private JsonNode actionValue(JsonNode value) {
        if (value.isString()) {
            try {
                return mapper.readTree(value.asString());
            } catch (RuntimeException e) {
                return mapper.createObjectNode();
            }
        }
        return value;
    }

    private Reply toast(String type, String content) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("toast").put("type", type).put("content", content);
        return new Reply(200, root);
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private static Long longOf(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber() && node.canConvertToLong()) {
            return node.asLong();
        }
        if (node.isString()) {
            try {
                return Long.parseLong(node.asString().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
