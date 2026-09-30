package cn.net.mxz.timeimprint.task.domain.shared.response;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 信封 {@code message} 文案（docs/04-API.md §2）：简体中文，供人与 AI 编排阅读。
 *
 * <p>{@code code} 仍为稳定英文；本类只负责可读 {@code message}。
 */
public final class ApiMessages {

    private static final Pattern COMMAND_NOT_DECLARED =
            Pattern.compile("scenario does not declare commandKey=(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPECTED_REVISION =
            Pattern.compile("expectedRevision=(\\d+)\\s+current=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETE_REQUIRES =
            Pattern.compile("complete requires PENDING, got (\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIP_REQUIRES =
            Pattern.compile("skip requires PENDING, got (\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNOOZE_REQUIRES =
            Pattern.compile("snooze requires PENDING, got (\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SNOOZE_DEF_STATE =
            Pattern.compile("snooze not allowed when definition is (\\S+)", Pattern.CASE_INSENSITIVE);

    private ApiMessages() {}

    public static String ok(String actionSummary) {
        if (actionSummary == null || actionSummary.isBlank()) {
            return "操作已成功完成";
        }
        return actionSummary.trim();
    }

    /**
     * 将业务异常详情规范为中文 message；未知英文细节回退到错误码默认说明。
     */
    public static String error(String code, String detail) {
        String safe = sanitize(detail);
        String mapped = mapKnownDetail(safe);
        if (mapped != null) {
            return mapped;
        }
        if (containsChinese(safe)) {
            return safe;
        }
        return defaultForCode(code);
    }

    public static String defaultForCode(String code) {
        if (code == null || code.isBlank()) {
            return "请求处理失败";
        }
        return switch (code) {
            case ApiErrorCodes.OK -> "操作已成功完成";
            case ApiErrorCodes.INVALID_REQUEST -> "请求不合法，请检查字段、类型或组合约束后重试";
            case ApiErrorCodes.INVALID_CURSOR -> "分页游标无效或不匹配当前过滤条件，请从头重新拉取";
            case ApiErrorCodes.UNSUPPORTED_SCHEMA_VERSION -> "请求的 schema 版本不受支持";
            case ApiErrorCodes.UNAUTHENTICATED -> "缺少可信调用身份";
            case ApiErrorCodes.FORBIDDEN -> "当前身份无权执行该操作";
            case ApiErrorCodes.RESOURCE_NOT_FOUND -> "资源不存在，或对当前调用方不可见";
            case ApiErrorCodes.EXTENSION_NOT_FOUND -> "场景、触发器或动作扩展未装配或不可用";
            case ApiErrorCodes.IDEMPOTENCY_CONFLICT -> "同一 requestId 对应不同请求摘要，请更换 requestId";
            case ApiErrorCodes.REVISION_CONFLICT -> "expectedRevision 与当前 revision 不一致，请先查询最新资源再重试";
            case ApiErrorCodes.STATE_CONFLICT -> "当前控制状态、生命周期或场景状态不允许该操作";
            case ApiErrorCodes.COMMAND_NOT_SUPPORTED ->
                    "当前场景未声明该 commandKey；请查询资源的 allowedCommands 后改用已声明命令";
            case ApiErrorCodes.REQUEST_TOO_LARGE -> "请求体超过 64KiB 上限";
            case ApiErrorCodes.UNSUPPORTED_MEDIA_TYPE -> "媒体类型不受支持，请使用 application/json";
            case ApiErrorCodes.POLICY_REJECTED -> "策略暂时拒绝该请求，可稍后重试";
            case ApiErrorCodes.INTERNAL_ERROR -> "服务内部错误，请稍后重试；响应不含内部细节";
            case ApiErrorCodes.RETRY_LATER -> "服务暂不可用或正在停机，请用同一 requestId 稍后重试";
            default -> "请求处理失败（" + code + "）";
        };
    }

    public static String commandNotSupported(String scenarioKey, String commandKey) {
        return "场景 "
                + scenarioKey
                + " 未声明命令 commandKey="
                + commandKey
                + "。请先查询该资源的 allowedCommands，改用已声明命令后再试";
    }

    public static String definitionCommandSucceeded(String commandKey, long definitionId, long revision, boolean changed) {
        if (!changed) {
            return "定义命令 "
                    + commandKey
                    + " 已受理且无状态变化（可能为幂等重放或 NoChange），definitionId="
                    + definitionId
                    + "，revision="
                    + revision;
        }
        return "定义命令 "
                + commandKey
                + " 已执行成功，definitionId="
                + definitionId
                + "，当前 revision="
                + revision;
    }

    public static String instanceCommandSucceeded(
            String commandKey, long instanceId, long revision, boolean changed, String scenarioState) {
        if (!changed) {
            return "实例命令 "
                    + commandKey
                    + " 已受理且无状态变化（可能为幂等重放或 NoChange），instanceId="
                    + instanceId
                    + "，revision="
                    + revision
                    + "，scenarioState="
                    + scenarioState;
        }
        return "实例命令 "
                + commandKey
                + " 已执行成功，instanceId="
                + instanceId
                + "，当前 revision="
                + revision
                + "，scenarioState="
                + scenarioState;
    }

    private static String mapKnownDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        String d = detail.trim();
        Matcher m;

        m = COMMAND_NOT_DECLARED.matcher(d);
        if (m.find()) {
            return "当前场景未声明命令 commandKey="
                    + m.group(1)
                    + "。请查询资源的 allowedCommands，改用已声明命令后再试";
        }
        m = EXPECTED_REVISION.matcher(d);
        if (m.find()) {
            return "实例修订号不一致：期望 expectedRevision="
                    + m.group(1)
                    + "，当前 revision="
                    + m.group(2)
                    + "。请先查询实例再重试";
        }
        m = COMPLETE_REQUIRES.matcher(d);
        if (m.find()) {
            return "complete 要求场景状态为 PENDING，当前为 " + m.group(1);
        }
        m = SKIP_REQUIRES.matcher(d);
        if (m.find()) {
            return "skip 要求场景状态为 PENDING，当前为 " + m.group(1);
        }
        m = SNOOZE_REQUIRES.matcher(d);
        if (m.find()) {
            return "snooze 要求场景状态为 PENDING，当前为 " + m.group(1);
        }
        m = SNOOZE_DEF_STATE.matcher(d);
        if (m.find()) {
            return "定义处于 " + m.group(1) + " 时不允许 snooze";
        }

        String lower = d.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "validation failed" -> "请求参数校验失败";
            case "invalid json" -> "请求体不是合法 JSON，或含未知字段/重复键/非法类型";
            case "unsupported media type" -> "媒体类型不受支持，请使用 application/json";
            case "method not allowed" -> "HTTP 方法不被允许；公开写操作请使用 POST，读操作使用 GET";
            case "not found" -> "路径或资源不存在";
            case "internal error" -> "服务内部错误，请稍后重试；响应不含内部细节";
            case "error" -> null;
            case "dedup in progress" -> "相同幂等键的请求正在处理中，请稍后用同一 requestId 重试";
            case "same requestid different payload" -> "同一 requestId 对应不同请求摘要，请更换 requestId";
            case "definition revision mismatch" -> "定义修订号不一致，请先查询最新 revision 再重试";
            case "instance not found", "instance", "definition", "signal", "action" -> "资源不存在，或对当前调用方不可见";
            case "skip reason is required" -> "skip 命令必须在 payload.reason 提供跳过原因";
            case "skip reason exceeds 500 code points" -> "skip 的 reason 超过 500 个 Unicode 码点上限";
            case "snoozeuntil is required" -> "snooze 命令必须提供未来的 snoozeUntil";
            case "maxsnoozecount exceeded" -> "已达到 maxSnoozeCount，不能再次 snooze";
            case "no movable actions" -> "没有可平移的 READY/RETRY_WAIT Action，无法 snooze";
            case "instance expiresat missing" -> "实例缺少 expiresAt，无法执行 snooze";
            case "too many participants" -> "参与人数量超过上限，请减少 participants 后重试";
            case "too many trigger bindings" -> "触发绑定数量超过上限";
            case "description too large" -> "description 体积超过单 JSON 上限";
            case "scenarioconfig too large" -> "scenarioConfig 体积超过单 JSON 上限";
            case "request body exceeds 64kib" -> "请求体超过 64KiB 上限";
            case "shutting down; retry with same requestid" -> "服务正在停机拒写，请用同一 requestId 稍后重试";
            case "retired cannot update" -> "已 RETIRED 的定义不能再 update";
            case "commandschemaversion must be 1" -> "commandSchemaVersion 必须为 1";
            case "control command payload must be empty object" -> "控制命令的 payload 必须为空对象 {}";
            case "owner required" -> "创建定义时必须包含 OWNER 参与人";
            case "only user principal supported" -> "参与人 principalType 目前仅支持 USER";
            case "local actor must match participants" -> "本地身份下 participants 必须匹配当前固定 actor";
            case "calendar signal requires instance" -> "日历 Signal 必须绑定 instanceId";
            case "signal token lost" -> "Signal 租约令牌已失效，请等待 Worker 重试或稍后手动 process";
            case "idempotent replay failed" -> "幂等重放失败，请检查首次结果或更换 requestId";
            case "bad payload json" -> "payload 不是合法 JSON";
            case "invalid scenarioconfig" -> "scenarioConfig 不合法";
            case "invalid trigger binding config" -> "触发绑定 config 不合法";
            case "once must be strictly after create time" -> "ONCE 发生时刻必须严格晚于创建事务时间";
            case "once must be strictly after update time" -> "ONCE 发生时刻必须严格晚于更新事务时间";
            case "s01 requires exactly one trigger binding", "exactly one trigger binding required" ->
                    "必须且只能提供一个触发绑定";
            case "expected calendar/primary binding" -> "首期必须使用 bindingKey=primary 且 providerKey=calendar";
            case "no actor" -> "缺少可信调用身份";
            case "cursor" -> "分页游标无效或不匹配当前过滤条件，请从头重新拉取";
            case "lock retry exhausted" -> "数据库锁重试耗尽，请用同一 requestId 稍后重试";
            default -> {
                if (lower.startsWith("definition command '") && lower.contains("' not supported")) {
                    yield "定义命令不受支持。请使用 update/pause/resume/retire";
                }
                yield null;
            }
        };
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String trimmed = message.length() > 512 ? message.substring(0, 512) : message;
        if (trimmed.contains("\tat ")
                || trimmed.toLowerCase(Locale.ROOT).contains("jdbc:")
                || trimmed.toLowerCase(Locale.ROOT).contains("password")) {
            return "";
        }
        return trimmed.trim();
    }

    private static boolean containsChinese(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
