package cn.net.mxz.timeimprint.task.service.capability.notification.notification.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.mxz.timeimprint.task.service.capability.notification.notification.configuration.NotificationDeliveryProperties;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.DeliveryChannel;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationDeliveryChannelAdapterTest {

    @Test
    void defaultMapsToInAppOnlyWithLegacyActionKeySegment() {
        var adapter = new NotificationDeliveryChannelAdapter(new NotificationDeliveryProperties());
        assertEquals(1, adapter.enabledChannels().size());
        DeliveryChannel c = adapter.enabledChannels().getFirst();
        assertEquals("IN_APP", c.channelKey());
        assertEquals("in_app_notification", c.handlerKey());
        assertEquals(ActionExecutionMode.LOCAL_TRANSACTIONAL, c.executionMode());
        assertEquals("in_app_notification", c.actionKeySegment());
    }

    @Test
    void feishuMapsToExternalHandler() {
        List<DeliveryChannel> channels = NotificationDeliveryChannelAdapter.resolve(List.of("IN_APP", "FEISHU"));
        assertEquals(List.of("IN_APP", "FEISHU"), channels.stream().map(DeliveryChannel::channelKey).toList());
        DeliveryChannel feishu = channels.get(1);
        assertEquals("feishu_im_notification", feishu.handlerKey());
        assertEquals(ActionExecutionMode.EXTERNAL, feishu.executionMode());
        assertEquals("FEISHU", feishu.actionKeySegment());
    }
}
