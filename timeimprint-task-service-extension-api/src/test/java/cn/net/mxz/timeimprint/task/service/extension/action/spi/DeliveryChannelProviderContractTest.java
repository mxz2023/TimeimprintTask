package cn.net.mxz.timeimprint.task.service.extension.action.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.net.mxz.timeimprint.task.service.extension.action.registry.DeliveryChannel;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryChannelProviderContractTest {

    @Test
    void exposesEnabledChannelsInOrder() {
        DeliveryChannel inApp = new DeliveryChannel(
                "IN_APP", "in_app_notification", ActionExecutionMode.LOCAL_TRANSACTIONAL, "in_app_notification");
        DeliveryChannelProvider provider = () -> List.of(inApp);
        assertEquals(List.of(inApp), provider.enabledChannels());
    }
}
