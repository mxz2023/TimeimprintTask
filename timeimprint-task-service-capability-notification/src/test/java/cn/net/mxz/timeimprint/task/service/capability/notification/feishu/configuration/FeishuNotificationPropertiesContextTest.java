package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** 03 §3.1 飞书配置键绑定与启用后的启动校验。 */
class FeishuNotificationPropertiesContextTest {

    private static FeishuNotificationProperties bind(Map<String, Object> src) {
        return new Binder(new MapConfigurationPropertySource(src))
                .bind("timeimprint.notification.feishu", FeishuNotificationProperties.class)
                .orElseGet(FeishuNotificationProperties::new);
    }

    @Test
    void defaultsMatchContract() {
        FeishuNotificationProperties p = new FeishuNotificationProperties();
        assertEquals("https://open.feishu.cn", p.getBaseUrl());
        assertEquals(5, p.getTimeoutSeconds());
        assertEquals(Map.of(), p.getRecipientMap());
        assertNull(p.openIdFor("x"));
    }

    @Test
    void bindsAllKeysIncludingRecipientMap() {
        FeishuNotificationProperties p = bind(Map.of(
                "timeimprint.notification.feishu.app-id", "cli_1",
                "timeimprint.notification.feishu.app-secret", "s",
                "timeimprint.notification.feishu.verification-token", "vt",
                "timeimprint.notification.feishu.encrypt-key", "ek",
                "timeimprint.notification.feishu.base-url", "http://localhost:9",
                "timeimprint.notification.feishu.timeout-seconds", "3",
                "timeimprint.notification.feishu.recipient-map.local-actor", "ou_1"));
        assertEquals("cli_1", p.getAppId());
        assertEquals("vt", p.getVerificationToken());
        assertEquals("ek", p.getEncryptKey());
        assertEquals(3, p.getTimeoutSeconds());
        assertEquals("ou_1", p.openIdFor("local-actor"));
        assertNull(p.openIdFor("other"));
        assertEquals("http://localhost:9", p.adapterProperties().baseUrl());
        assertEquals("cli_1", p.credentials().appId());
        assertDoesNotThrow(() -> p.validateForOutbound(10));
    }

    @Test
    void validationRequiresCredentialsButNotRecipientMap() {
        FeishuNotificationProperties p = new FeishuNotificationProperties();
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setAppId("a");
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setAppSecret("s");
        assertDoesNotThrow(() -> p.validateForOutbound(10));
    }

    @Test
    void validationRejectsBadBaseUrlTimeoutAndBlankMapEntries() {
        FeishuNotificationProperties p = new FeishuNotificationProperties();
        p.setAppId("a");
        p.setAppSecret("s");
        p.setBaseUrl("ftp://x");
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setBaseUrl("not a url");
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setBaseUrl("https://open.feishu.cn");
        p.setTimeoutSeconds(6);
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setTimeoutSeconds(0);
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
        p.setTimeoutSeconds(5);
        p.setRecipientMap(Map.of("u", " "));
        assertThrows(IllegalStateException.class, () -> p.validateForOutbound(10));
    }
}
