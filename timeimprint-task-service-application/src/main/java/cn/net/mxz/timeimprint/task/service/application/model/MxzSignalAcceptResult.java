package cn.net.mxz.timeimprint.task.service.application.model;

import java.time.Instant;

public record MxzSignalAcceptResult(
        long signalId, boolean duplicated, String processStatus, Instant receivedAt) {}
