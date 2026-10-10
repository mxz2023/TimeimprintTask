package cn.net.mxz.timeimprint.task.identity.account.service;

import java.time.LocalDateTime;

/** 账号行。actorKey 是任务参与人和 ActorContext 使用的公开标识。 */
public record IdentityUser(
        long userId,
        String tenantId,
        String actorKey,
        String phoneNumber,
        String username,
        String passwordHash,
        String nickname,
        String accountStatus,
        boolean admin,
        LocalDateTime authorizationVerifiedAt) {

    public boolean active() {
        return "ACTIVE".equals(accountStatus);
    }

    public boolean authorized() {
        return authorizationVerifiedAt != null;
    }
}
