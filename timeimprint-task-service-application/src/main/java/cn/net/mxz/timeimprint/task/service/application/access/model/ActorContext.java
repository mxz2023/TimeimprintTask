package cn.net.mxz.timeimprint.task.service.application.access.model;

/** 已鉴权的调用方身份视图（不含 HTTP 或环境适配细节）。 */
public record ActorContext(String principalType, String principalId, String tenantKey) {
    public ActorContext {
        if (principalType == null || principalType.isBlank()) {
            throw new IllegalArgumentException("principalType required");
        }
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId required");
        }
        tenantKey = tenantKey == null ? "" : tenantKey;
    }
}
