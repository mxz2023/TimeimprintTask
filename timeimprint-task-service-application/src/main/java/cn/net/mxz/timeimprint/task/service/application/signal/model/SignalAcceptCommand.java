package cn.net.mxz.timeimprint.task.service.application.signal.model;

import java.time.Instant;

public record SignalAcceptCommand(
        String tenantId,
        String actorId,
        String requestId,
        String providerKey,
        String signalKey,
        int schemaVersion,
        Instant occurredAt,
        long definitionId,
        Long instanceId,
        String payloadJson,
        Instant now) {}
