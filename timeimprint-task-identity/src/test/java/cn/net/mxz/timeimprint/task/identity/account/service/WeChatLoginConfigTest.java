package cn.net.mxz.timeimprint.task.identity.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class WeChatLoginConfigTest {

    @Test
    void holdsAppTypeStateAndAuthUrlWithoutSecret() {
        WeChatLoginConfig config = new WeChatLoginConfig("web", "state-1", "https://open.weixin.qq.com/connect/qrconnect");
        assertEquals("web", config.appType());
        assertEquals("state-1", config.state());
        assertEquals("https://open.weixin.qq.com/connect/qrconnect", config.authUrl());
        assertFalse(config.authUrl().contains("secret"));
    }
}
