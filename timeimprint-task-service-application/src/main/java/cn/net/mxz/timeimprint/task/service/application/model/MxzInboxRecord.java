package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzInboxRecord(
        long inboxId,
        String tenantId,
        long notificationId,
        long actionJobId,
        long definitionId,
        long instanceId,
        String scenarioKey,
        String purpose,
        String title,
        String body,
        String recipientType,
        String recipientId,
        Instant readAt,
        Instant createdAt) {}
