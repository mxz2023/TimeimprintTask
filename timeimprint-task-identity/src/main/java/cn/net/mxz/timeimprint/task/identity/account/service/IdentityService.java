package cn.net.mxz.timeimprint.task.identity.account.service;

import cn.net.mxz.timeimprint.task.adapter.sms.client.SmsSender;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityStore store;
    private final SmsSender smsSender;
    private final WeChatOAuthClient weChat;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String tenantId;
    private final String authorizationString;

    public IdentityService(
            IdentityStore store,
            SmsSender smsSender,
            WeChatOAuthClient weChat,
            @Value("${timeimprint.local.tenant-id:local-tenant}") String tenantId,
            @Value("${timeimprint.identity.authorization-string:local-dev-authorization}") String authorizationString) {
        this.store = store;
        this.smsSender = smsSender;
        this.weChat = weChat;
        this.tenantId = tenantId;
        this.authorizationString = authorizationString;
    }

    public String tenantId() {
        return tenantId;
    }

    public void sendSms(String phone) {
        requirePhone(phone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        if (!smsSender.send(phone, code)) {
            throw new IdentityException("INVALID_REQUEST", "验证码发送失败，请稍后再试");
        }
        store.insertCode(phone, code, IdentityStore.utcNow());
    }

    public void verifySms(String phone, String code) {
        requirePhone(phone);
        requireFreshCode(phone, code);
    }

    @Transactional
    public LoginResult register(String phone, String code, String password, String nickname) {
        requirePhone(phone);
        requirePassword(password);
        requireFreshCode(phone, code);
        if (store.findByPhone(tenantId, phone).isPresent()) {
            throw new IdentityException("STATE_CONFLICT", "该手机号已注册");
        }
        boolean first = store.countUsers() == 0;
        String nick = nickname == null || nickname.isBlank() ? "用户" : nickname.trim();
        if (nick.length() > 30) {
            throw new IdentityException("INVALID_REQUEST", "昵称最长 30 字");
        }
        long id = store.insertUser(
                new IdentityUser(
                        0,
                        tenantId,
                        "u-" + HexFormat.of().formatHex(randomBytes(8)),
                        phone,
                        phone,
                        passwords.encode(password),
                        nick,
                        "ACTIVE",
                        first,
                        null),
                IdentityStore.utcNow());
        return loginResult(store.findById(id).orElseThrow(), "phone");
    }

    public LoginResult login(String phone, String password) {
        requirePhone(phone);
        requirePassword(password);
        IdentityUser user = store.findByPhone(tenantId, phone)
                .filter(IdentityUser::active)
                .filter(u -> u.passwordHash() != null && passwords.matches(password, u.passwordHash()))
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "手机号或密码错误"));
        return loginResult(user, "phone");
    }

    @Transactional
    public LoginResult authorize(String challenge, String verificationString) {
        if (authorizationString.isBlank()) {
            throw new IdentityException("INVALID_REQUEST", "登录授权功能尚未配置，请联系管理员");
        }
        String[] parts = verifyChallenge(challenge);
        if (!constantTimeEquals(verificationString, authorizationString)) {
            throw new IdentityException("UNAUTHENTICATED", "授权验证失败，无法进入");
        }
        long userId = Long.parseLong(parts[0]);
        IdentityUser user = store.findById(userId)
                .filter(IdentityUser::active)
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "账号不可用，请重新登录"));
        if (!user.authorized()) {
            store.markAuthorized(user.userId(), IdentityStore.utcNow());
            user = store.findById(user.userId()).orElseThrow();
        }
        return issueToken(user);
    }

    public IdentityUser requireActor(String tenantId, String actorKey) {
        return store.findByActor(tenantId, actorKey)
                .filter(IdentityUser::active)
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "没有可信调用身份"));
    }

    public IdentityUser requireSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IdentityException("UNAUTHENTICATED", "没有可信调用身份");
        }
        IdentityUser user = store.findByTokenHash(sha256(rawToken))
                .filter(IdentityUser::active)
                .orElseThrow(() -> new IdentityException("UNAUTHENTICATED", "登录已失效，请重新登录"));
        return user;
    }

    public void logout(IdentityUser user) {
        store.deleteSessions(user.userId());
    }

    public IdentityUser me(IdentityUser user) {
        return user;
    }

    public void changePassword(IdentityUser user, String oldPassword, String password) {
        requirePassword(password);
        if (user.passwordHash() == null || !passwords.matches(oldPassword, user.passwordHash())) {
            throw new IdentityException("INVALID_REQUEST", "旧密码不正确");
        }
        store.updatePassword(user.userId(), passwords.encode(password));
    }

    public void changeNickname(IdentityUser user, String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > 30) {
            throw new IdentityException("INVALID_REQUEST", "昵称最长 30 字");
        }
        store.updateNickname(user.userId(), nickname.trim());
    }

    @Transactional
    public void resetPassword(String phone, String code, String password) {
        requirePhone(phone);
        requirePassword(password);
        requireFreshCode(phone, code);
        IdentityUser user = store.findByPhone(tenantId, phone)
                .orElseThrow(() -> new IdentityException("INVALID_REQUEST", "用户不存在"));
        store.updatePassword(user.userId(), passwords.encode(password));
    }

    public List<IdentityUser> listUsers(IdentityUser admin, String keyword, int page, int pageSize) {
        if (!admin.admin()) {
            throw new IdentityException("FORBIDDEN", "只有管理员可以查看用户列表");
        }
        int size = Math.min(100, Math.max(1, pageSize));
        int p = Math.max(1, page);
        return store.search(keyword == null ? "" : keyword.trim(), (p - 1) * size, size);
    }

    public int countUsers(IdentityUser admin, String keyword) {
        if (!admin.admin()) {
            throw new IdentityException("FORBIDDEN", "只有管理员可以查看用户列表");
        }
        return store.countSearch(keyword == null ? "" : keyword.trim());
    }

    @Transactional
    public LoginResult wechatCallback(String appType, String code, String state) {
        WeChatProfile profile = exchange(appType, code, state);
        Optional<Long> existing = store.findSocialUserId("wechat", profile.appType(), profile.openid(), profile.unionid());
        IdentityUser user;
        if (existing.isPresent()) {
            user = store.findById(existing.get()).orElseThrow();
        } else {
            long id = store.insertUser(
                    new IdentityUser(
                            0,
                            tenantId,
                            "wx-" + profile.openid(),
                            null,
                            "wx_" + profile.appType() + "_" + profile.openid(),
                            null,
                            profile.nickname() == null ? "微信用户" : profile.nickname(),
                            "ACTIVE",
                            store.countUsers() == 0,
                            null),
                    IdentityStore.utcNow());
            store.insertSocial(id, "wechat", profile.appType(), profile.openid(), profile.unionid(), profile.nickname(), profile.avatarUrl());
            user = store.findById(id).orElseThrow();
        }
        if (!user.active()) {
            throw new IdentityException("INVALID_REQUEST", "该账号已注销，暂时无法登录");
        }
        return loginResult(user, "wechat");
    }

    @Transactional
    public void bindWeChat(IdentityUser user, String appType, String code, String state) {
        WeChatProfile profile = exchange(appType, code, state);
        Optional<Long> existing = store.findSocialUserId("wechat", profile.appType(), profile.openid(), profile.unionid());
        if (existing.isPresent() && existing.get() != user.userId()) {
            throw new IdentityException("STATE_CONFLICT", "该微信已绑定其他账号");
        }
        if (existing.isEmpty()) {
            store.insertSocial(
                    user.userId(), "wechat", profile.appType(), profile.openid(), profile.unionid(), profile.nickname(), profile.avatarUrl());
        }
    }

    @Transactional
    public void unbindWeChat(IdentityUser user) {
        boolean hasPhone = user.phoneNumber() != null && !user.phoneNumber().isBlank();
        boolean hasPassword = user.passwordHash() != null && !user.passwordHash().isBlank();
        if (!hasPhone && !hasPassword) {
            throw new IdentityException("INVALID_REQUEST", "请先绑定手机号或设置密码，再解绑微信");
        }
        if (store.deleteSocial(user.userId(), "wechat") == 0) {
            throw new IdentityException("INVALID_REQUEST", "当前账号未绑定微信");
        }
    }

    @Transactional
    public LoginResult bindPhone(IdentityUser current, String phone, String code, String password) {
        requirePhone(phone);
        requirePassword(password);
        requireFreshCode(phone, code);
        Optional<IdentityUser> target = store.findByPhone(tenantId, phone);
        if (target.isPresent() && target.get().userId() != current.userId()) {
            IdentityUser dest = target.get();
            if (!dest.active()) {
                throw new IdentityException("INVALID_REQUEST", "该手机号账号已注销，暂时无法关联");
            }
            if (current.phoneNumber() != null && !current.phoneNumber().isBlank()) {
                throw new IdentityException("STATE_CONFLICT", "该手机号已绑定其他账号");
            }
            store.moveSocial(current.userId(), dest.userId(), "wechat");
            store.updatePassword(dest.userId(), passwords.encode(password));
            if (current.authorized() && !dest.authorized()) {
                store.markAuthorized(dest.userId(), current.authorizationVerifiedAt());
            }
            store.deleteSessions(current.userId());
            store.disable(current.userId(), "merged_" + current.userId() + "_" + current.username());
            return issueToken(store.findById(dest.userId()).orElseThrow());
        }
        store.updatePhoneAndUsername(current.userId(), phone);
        store.updatePassword(current.userId(), passwords.encode(password));
        return issueToken(store.findById(current.userId()).orElseThrow());
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

    private LoginResult loginResult(IdentityUser user, String provider) {
        if (user.authorized()) {
            return issueToken(user);
        }
        store.deleteSessions(user.userId());
        long exp = System.currentTimeMillis() + CHALLENGE_TTL.toMillis();
        String payload = user.userId() + "|" + exp + "|" + provider;
        String mac = hmac(payload);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "|" + mac).getBytes(StandardCharsets.UTF_8));
        return new LoginResult(true, challenge, null, user.actorKey(), user.nickname(), user.phoneNumber(), user.admin());
    }

    private LoginResult issueToken(IdentityUser user) {
        byte[] raw = randomBytes(32);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        store.deleteSessions(user.userId());
        store.insertSession(user.userId(), sha256(token), IdentityStore.utcNow());
        store.touchLogin(user.userId(), IdentityStore.utcNow());
        return new LoginResult(false, null, token, user.actorKey(), user.nickname(), user.phoneNumber(), user.admin());
    }

    private String[] verifyChallenge(String challenge) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(challenge), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 4) {
                throw new IdentityException("UNAUTHENTICATED", "授权验证无效，请重新登录");
            }
            String payload = parts[0] + "|" + parts[1] + "|" + parts[2];
            if (!constantTimeEquals(parts[3], hmac(payload))) {
                throw new IdentityException("UNAUTHENTICATED", "授权验证无效，请重新登录");
            }
            if (Long.parseLong(parts[1]) < System.currentTimeMillis()) {
                throw new IdentityException("UNAUTHENTICATED", "授权验证已过期，请重新登录");
            }
            return parts;
        } catch (IdentityException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IdentityException("UNAUTHENTICATED", "授权验证无效，请重新登录");
        }
    }

    private void requireFreshCode(String phone, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IdentityException("INVALID_REQUEST", "验证码错误");
        }
        LocalDateTime created = store.latestCodeAt(phone, code)
                .orElseThrow(() -> new IdentityException("INVALID_REQUEST", "验证码错误"));
        if (Duration.between(created, IdentityStore.utcNow()).compareTo(CODE_TTL) > 0) {
            throw new IdentityException("INVALID_REQUEST", "验证码已过期");
        }
    }

    private static void requirePhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new IdentityException("INVALID_REQUEST", "请输入正确手机号");
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank() || password.length() > 128) {
            throw new IdentityException("INVALID_REQUEST", "请输入正确密码");
        }
    }

    private WeChatProfile exchange(String appType, String code, String state) {
        try {
            String type = appType == null || appType.isBlank() ? "web" : appType;
            return weChat.exchange(type, code, state);
        } catch (IllegalStateException ex) {
            throw new IdentityException("INVALID_REQUEST", ex.getMessage());
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authorizationString.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] raw = new byte[n];
        RANDOM.nextBytes(raw);
        return raw;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
