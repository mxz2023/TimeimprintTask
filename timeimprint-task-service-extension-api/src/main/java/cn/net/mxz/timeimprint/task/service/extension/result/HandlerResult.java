package cn.net.mxz.timeimprint.task.service.extension.result;

import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;

/**
 * 场景/命令/Signal 纯计算结果：Applied、NoChange 或 Rejected（可预期业务拒绝，非技术异常）。
 */
public sealed interface HandlerResult permits HandlerResult.Applied, HandlerResult.NoChange, HandlerResult.Rejected {

    record Applied(TransitionPlan plan) implements HandlerResult {}

    record NoChange(Object result) implements HandlerResult {}

    record Rejected(String reasonCode, String safeMessage) implements HandlerResult {}
}
