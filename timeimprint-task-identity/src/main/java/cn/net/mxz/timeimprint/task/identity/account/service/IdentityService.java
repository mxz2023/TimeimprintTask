package cn.net.mxz.timeimprint.task.identity.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预置本机测试账号。登录令牌只由 TimeImprintUser 保存，任务请求不在本库查询。
 * 既有账号的 actor_key 仍是 wx-、u- 或预置 local-actor。
 * 注册、短信和微信登录已由 TimeImprintUser 提供，这里不再实现。
 */
@Service
public class IdentityService {

    private final IdentityStore store;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public IdentityService(IdentityStore store) {
        this.store = store;
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
