package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzAttemptRecord(
        int attemptNo,
        Instant startedAt,
        Instant finishedAt,
        Instant effectStartedAt,
        String outcome,
        String errorClass,
        String errorCode,
        String providerReference,
        String safeSummary) {}
