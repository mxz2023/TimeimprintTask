package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import java.time.Instant;

/**
 * 待创建 Action Job 的声明式意图。
 * T02 回正：补充 executionMode / availableAt / expiresAt，与 05/06 对齐。
 */
public record ActionJobIntent(
        String handlerKey,
        int actionSchemaVersion,
        String actionKey,
        String executionMode,
        String targetType,
        String targetId,
        Instant availableAt,
        Instant expiresAt,
        ScenarioMutationPayload payload) {}
