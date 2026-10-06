package cn.net.mxz.timeimprint.task.service.capability.notification.notification.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** F01：delivery-channels 默认仅 IN_APP；非法配置启动即失败。 */
class NotificationDeliveryPropertiesContextTest {

    @Test
    void defaultsToInAppOnly() {
        NotificationDeliveryProperties p = new NotificationDeliveryProperties();
        assertEquals(List.of("IN_APP"), p.getDeliveryChannels());
        assertDoesNotThrow(p::afterPropertiesSet);
    }

    @Test
    void bindsYamlStyleListAndAcceptsInAppPlusFeishu() {
        Map<String, Object> src = Map.of(
                "timeimprint.notification.delivery-channels[0]", "IN_APP",
                "timeimprint.notification.delivery-channels[1]", "FEISHU");
        NotificationDeliveryProperties p = new Binder(new MapConfigurationPropertySource(src))
                .bind("timeimprint.notification", NotificationDeliveryProperties.class)
                .get();
        assertEquals(List.of("IN_APP", "FEISHU"), p.getDeliveryChannels());
        assertDoesNotThrow(p::afterPropertiesSet);
    }

    @Test
    void rejectsEmptyMissingInAppUnknownAndDuplicate() {
        assertThrows(IllegalStateException.class, () -> NotificationDeliveryProperties.validate(List.of()));
        assertThrows(IllegalStateException.class, () -> NotificationDeliveryProperties.validate(null));
        assertThrows(IllegalStateException.class, () -> NotificationDeliveryProperties.validate(List.of("FEISHU")));
        assertThrows(
                IllegalStateException.class,
                () -> NotificationDeliveryProperties.validate(List.of("IN_APP", "WECHAT")));
        assertThrows(
                IllegalStateException.class,
                () -> NotificationDeliveryProperties.validate(List.of("IN_APP", "IN_APP")));
    }

    @Test
    void invalidBoundConfigFailsFast() {
        NotificationDeliveryProperties p = new NotificationDeliveryProperties();
        p.setDeliveryChannels(List.of("FEISHU"));
        assertThrows(IllegalStateException.class, p::afterPropertiesSet);
    }
}
