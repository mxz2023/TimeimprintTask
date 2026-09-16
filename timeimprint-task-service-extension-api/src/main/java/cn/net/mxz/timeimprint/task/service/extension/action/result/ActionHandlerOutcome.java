package cn.net.mxz.timeimprint.task.service.extension.action.result;

/** ActionHandler 单次调用结果分类（与 {@link cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult} 分离）。 */
public enum ActionHandlerOutcome {
    SUCCEEDED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    UNKNOWN
}
