package cn.net.mxz.timeimprint.task.service.kernel.shared.model;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import java.time.Instant;

/** 后续持久化 Signal 的声明（去重键由 providerKey + signalKey 等构成）。 */
public record PlannedSignalIntent(
        String providerKey,
        String signalKey,
        int schemaVersion,
        long definitionId,
        Long instanceId,
        Instant occurredAt,
        ScenarioMutationPayload payload) {}
