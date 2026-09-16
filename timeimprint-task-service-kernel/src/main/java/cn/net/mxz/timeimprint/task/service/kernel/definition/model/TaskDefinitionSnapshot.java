package cn.net.mxz.timeimprint.task.service.kernel.definition.model;

import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import java.time.Instant;

/** 锁内或读路径使用的定义快照。 */
public record TaskDefinitionSnapshot(
        long definitionId,
        String tenantId,
        String scenarioKey,
        int scenarioSchemaVersion,
        String title,
        String description,
        String scenarioConfigJson,
        ControlState controlState,
        long controlGeneration,
        long revision,
        Instant createdAt,
        Instant updatedAt) {}
