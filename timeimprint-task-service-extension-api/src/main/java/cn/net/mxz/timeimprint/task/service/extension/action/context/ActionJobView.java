package cn.net.mxz.timeimprint.task.service.extension.action.context;

import java.time.Instant;

/** 命令锁内可见的 Action 摘要（只读，供场景命令做纯计算）。 */
public record ActionJobView(
        long actionJobId,
        String actionKey,
        String status,
        Instant availableAt,
        Instant expiresAt,
        String payloadJson) {}
