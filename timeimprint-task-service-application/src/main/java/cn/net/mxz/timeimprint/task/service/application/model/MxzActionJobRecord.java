package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzActionJobRecord(
        long actionJobId,
        String tenantId,
        long definitionId,
        long instanceId,
        long transitionId,
        long definitionControlGeneration,
        String handlerKey,
        String actionKey,
        String executionMode,
        int schemaVersion,
        String targetType,
        String targetId,
        String payloadJson,
        Instant availableAt,
        Instant expiresAt,
        String status,
        int attemptCount,
        int maxAttempts) {}
