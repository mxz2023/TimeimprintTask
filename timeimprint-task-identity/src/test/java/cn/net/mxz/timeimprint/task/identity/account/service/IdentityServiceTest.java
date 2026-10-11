package cn.net.mxz.timeimprint.task.identity.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.wechat.client.CaptureWeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.sms.client.CaptureSmsSender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityServiceTest {

    @Test
    void hashesTokenStably() {
        assertEquals(32, IdentityService.sha256("it-token-local-actor").length);
    }

    @Test
    void captureWechatConfigDoesNotPersistOauthState() {
        RecordingStore store = new RecordingStore();
        IdentityService service = service(store, "capture", "wx-app", "https://cb");
        WeChatLoginConfig config = service.wechatConfig("web");
        assertEquals("capture-state", config.state());
        assertEquals("", config.authUrl());
        assertTrue(store.oauthInserts.isEmpty());
    }

    @Test
    void liveWechatConfigRejectsIos() {
        RecordingStore store = new RecordingStore();
        IdentityService service = service(store, "live", "wx-app", "https://cb.example/oauth");
        IdentityException ex = assertThrows(IdentityException.class, () -> service.wechatConfig("ios"));
        assertEquals("INVALID_REQUEST", ex.errorCode());
    }

    @Test
    void liveExchangeConsumesStateBeforeWeChatClient() {
        RecordingStore store = new RecordingStore();
        store.consumeOk = true;
        CountingWeChat weChat = new CountingWeChat();
        IdentityService service = new IdentityService(
                store,
                new CaptureSmsSender(),
                weChat,
                "local-tenant",
                "local-dev-authorization",
                "live",
                "wx-app",
                "https://cb.example/oauth");
        service.wechatCallback("web", "openid-x", "state-1");
        assertEquals(1, store.consumeAttempts);
        assertEquals(1, weChat.calls);
    }

    @Test
    void liveExchangeRejectsBadStateWithoutCallingWeChat() {
        RecordingStore store = new RecordingStore();
        store.consumeOk = false;
        CountingWeChat weChat = new CountingWeChat();
        IdentityService service = new IdentityService(
                store,
                new CaptureSmsSender(),
                weChat,
                "local-tenant",
                "local-dev-authorization",
                "live",
                "wx-app",
                "https://cb.example/oauth");
        IdentityException ex =
                assertThrows(IdentityException.class, () -> service.wechatCallback("web", "code", "bad-state"));
        assertEquals("INVALID_REQUEST", ex.errorCode());
        assertEquals(0, weChat.calls);
    }

    private static IdentityService service(RecordingStore store, String mode, String appId, String redirect) {
        return new IdentityService(
                store,
                new CaptureSmsSender(),
                new CaptureWeChatOAuthClient(),
                "local-tenant",
                "local-dev-authorization",
                mode,
                appId,
                redirect);
    }

    private static final class CountingWeChat implements WeChatOAuthClient {
        int calls;

        @Override
        public cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatProfile exchange(
                String appType, String code, String state) {
            calls++;
            return new CaptureWeChatOAuthClient().exchange(appType, code, state);
        }
    }

    private static final class RecordingStore extends IdentityStore {
        final List<String> oauthInserts = new ArrayList<>();
        int consumeAttempts;
        boolean consumeOk;

        RecordingStore() {
            super(null);
        }

        @Override
        public void insertOauthState(
                byte[] stateHash, String appType, java.time.LocalDateTime expiresAt, java.time.LocalDateTime createdAt) {
            oauthInserts.add(appType);
        }

        @Override
        public boolean consumeOauthState(byte[] stateHash, String appType, java.time.LocalDateTime consumedAt) {
            consumeAttempts++;
            return consumeOk;
        }

        @Override
        public long insertUser(IdentityUser draft, java.time.LocalDateTime now) {
            return 42L;
        }

        @Override
        public int countUsers() {
            return 0;
        }

        @Override
        public java.util.Optional<IdentityUser> findById(long userId) {
            return java.util.Optional.of(new IdentityUser(
                    userId, "local-tenant", "wx-openid-x", null, "wx_web_openid-x", null, "nick", "ACTIVE", false, null));
        }

        @Override
        public java.util.Optional<Long> findSocialUserId(String provider, String appType, String openid, String unionid) {
            return java.util.Optional.empty();
        }

        @Override
        public void insertSocial(
                long userId,
                String provider,
                String appType,
                String openid,
                String unionid,
                String nickname,
                String avatar) {}

        @Override
        public void deleteSessions(long userId) {}

        @Override
        public void insertSession(long userId, byte[] tokenHash, java.time.LocalDateTime now) {}

        @Override
        public void touchLogin(long userId, java.time.LocalDateTime when) {}
    }
}
