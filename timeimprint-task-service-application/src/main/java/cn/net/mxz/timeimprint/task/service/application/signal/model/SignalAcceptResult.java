package cn.net.mxz.timeimprint.task.service.application.signal.model;

import java.time.Instant;

public record SignalAcceptResult(
        long signalId, boolean duplicated, String processStatus, Instant receivedAt) {}
