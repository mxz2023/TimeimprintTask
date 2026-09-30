package cn.net.mxz.timeimprint.task.service.kernel.instance.model;

import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import java.time.Instant;

/** 锁内或读路径使用的实例快照。 */
public record TaskInstanceSnapshot(
        long instanceId,
        long definitionId,
        Long triggerBindingId,
        Long scheduleGeneration,
        long definitionControlGeneration,
        String occurrenceKey,
        Instant occurrenceAt,
        Instant dueAt,
        LifecycleCategory lifecycleCategory,
        String scenarioState,
        int scenarioSchemaVersion,
        String scenarioSnapshotJson,
        String titleSnapshot,
        String descriptionSnapshot,
        long revision,
        Instant terminalAt) {}
