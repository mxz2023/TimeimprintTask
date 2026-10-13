package cn.net.mxz.timeimprint.task.adapter.wechat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LiveWeChatOAuthClientTest {

    private static final String TOKEN_BODY =
            """
            {"access_token":"secret-access-token-value","expires_in":7200,"openid":"oid-live","unionid":"un-1"}
            """;
    private static final String USER_BODY =
            """
            {"openid":"oid-live","nickname":"昵称","headimgurl":"https://example.com/a.png"}
            """;

    @Test
    void exchangeUsesStubHttpAndOmitsTokenFromErrors() {
        LiveWeChatOAuthClient client = new LiveWeChatOAuthClient(
                "wx-app",
                "wx-secret",
                url -> {
                    if (url.contains("/sns/userinfo")) {
                        return USER_BODY;
                    }
                    if (url.contains("/oauth2/access_token")) {
                        return TOKEN_BODY;
                    }
                    throw new IllegalStateException("unexpected " + url);
                });
        WeChatProfile profile = client.exchange("web", "auth-code", "ignored-state");
        assertEquals("oid-live", profile.openid());
        assertEquals("昵称", profile.nickname());
    }

    @Test
    void weChatErrcodeThrowsSafeMessage() {
        LiveWeChatOAuthClient client = new LiveWeChatOAuthClient(
                "wx-app",
                "wx-secret",
                url -> "{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> client.exchange("web", "bad", "s"));
        assertEquals("微信授权无效", ex.getMessage());
        assertFalse(ex.getMessage().contains("access_token"));
    }

    @Test
    void buildAuthUrlContainsQrConnectAndAppId() {
        String url = LiveWeChatOAuthClient.buildQrConnectAuthUrl(
                "wx-app-id", "https://example.com/callback", "state-abc");
        assertTrue(url.startsWith("https://open.weixin.qq.com/connect/qrconnect?"));
        assertTrue(url.contains("appid=wx-app-id"));
        assertTrue(url.contains("snsapi_login"));
        assertTrue(url.contains("state-abc"));
        assertTrue(url.endsWith("#wechat_redirect"));
    }
}
