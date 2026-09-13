package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzSignalRecord(
        long signalId,
        String tenantId,
        long definitionId,
        Long triggerBindingId,
        Long instanceId,
        long definitionControlGeneration,
        String providerKey,
        String signalKey,
        int schemaVersion,
        Instant occurredAt,
        Instant receivedAt,
        String payloadJson,
        String processStatus,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        String resultCode,
        Instant processedAt,
        Long parentSignalId,
        int redriveNo,
        String leaseOwner,
        Instant leaseUntil,
        String executionToken,
        String resultSummary) {}
