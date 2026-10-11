package cn.net.mxz.timeimprint.task.identity.account.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.wechat.client.CaptureWeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.LiveWeChatOAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

class IdentityClientConfigurationContextTest {

    private final IdentityClientConfiguration configuration = new IdentityClientConfiguration();

    @Test
    void isConfiguration() {
        assertTrue(IdentityClientConfiguration.class.isAnnotationPresent(Configuration.class));
    }

    @Test
    void liveModeRequiresWebCredentials() {
        assertThrows(
                IllegalStateException.class,
                () -> configuration.weChatOAuthClient("live", "", "", ""));
    }

    @Test
    void captureAndLiveAssembleExpectedClients() {
        assertInstanceOf(
                CaptureWeChatOAuthClient.class, configuration.weChatOAuthClient("capture", "", "", ""));
        assertInstanceOf(
                LiveWeChatOAuthClient.class,
                configuration.weChatOAuthClient("live", "app", "secret", "https://example.com/cb"));
    }
}
