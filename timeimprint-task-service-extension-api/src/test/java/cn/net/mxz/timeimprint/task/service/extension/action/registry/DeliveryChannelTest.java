package cn.net.mxz.timeimprint.task.service.extension.action.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import org.junit.jupiter.api.Test;

class DeliveryChannelTest {

    @Test
    void holdsMappingFields() {
        DeliveryChannel c = new DeliveryChannel(
                "IN_APP", "in_app_notification", ActionExecutionMode.LOCAL_TRANSACTIONAL, "in_app_notification");
        assertEquals("IN_APP", c.channelKey());
        assertEquals(ActionExecutionMode.LOCAL_TRANSACTIONAL, c.executionMode());
    }

    @Test
    void rejectsBlankOrNullFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryChannel(" ", "h", ActionExecutionMode.EXTERNAL, "s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryChannel("FEISHU", "", ActionExecutionMode.EXTERNAL, "s"));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryChannel("FEISHU", "h", null, "s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryChannel("FEISHU", "h", ActionExecutionMode.EXTERNAL, null));
    }
}
