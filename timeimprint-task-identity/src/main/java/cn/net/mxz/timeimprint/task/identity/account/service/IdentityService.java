package cn.net.mxz.timeimprint.task.identity.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务接口仍用既有会话表解析令牌，并预置本机测试账号。
 * 既有会话的 actor_key 仍是 wx-、u- 或预置 local-actor。
 * 注册、短信和微信登录已由 TimeImprintUser 提供，这里不再实现。
 */
@Service
public class IdentityService {

    private final IdentityStore store;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public IdentityService(IdentityStore store) {
        this.store = store;
    }

    public IdentityUser requireSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IdentityException("UNAUTHENTICATED", "没有可信调用身份");
        }
        return store.findByTokenHash(sha256(rawToken))
                .filter(IdentityUser::active)
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "登录已失效，请重新登录"));
    }

    /** 测试与本地预置账号。已授权，并写入已知令牌。 */
    @Transactional
    public void bootstrap(String tenant, String actorKey, String rawToken) {
        if (store.findByActor(tenant, actorKey).isPresent()) {
            IdentityUser existing = store.findByActor(tenant, actorKey).orElseThrow();
            store.deleteSessions(existing.userId());
            store.insertSession(existing.userId(), sha256(rawToken), IdentityStore.utcNow());
            return;
        }
        LocalDateTime now = IdentityStore.utcNow();
        long id = store.insertUser(
                new IdentityUser(
                        0,
                        tenant,
                        actorKey,
                        null,
                        actorKey,
                        passwords.encode("it-password"),
                        actorKey,
                        "ACTIVE",
                        store.countUsers() == 0,
                        now),
                now);
        store.insertSession(id, sha256(rawToken), now);
    }

    static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
