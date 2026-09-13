package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzTransitionRecord(
        long transitionId,
        long definitionId,
        Long instanceId,
        String sourceType,
        String sourceKey,
        String commandKey,
        String fromControlState,
        String toControlState,
        String fromLifecycle,
        String toLifecycle,
        String fromScenarioState,
        String toScenarioState,
        long fromRevision,
        long toRevision,
        String actorType,
        String actorId,
        String traceId,
        Instant createdAt) {}
