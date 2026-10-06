package cn.net.mxz.timeimprint.task.service.capability.notification.notification.adapter;

import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler.FeishuImNotificationHandler;
import cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler.InAppNotificationHandler;
import cn.net.mxz.timeimprint.task.service.capability.notification.notification.configuration.NotificationDeliveryProperties;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.DeliveryChannel;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.spi.DeliveryChannelProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/** 把配置的渠道键映射为 {@link DeliveryChannel}（handlerKey / 执行模式 / actionKey 哈希末段）。 */
@Component
public class NotificationDeliveryChannelAdapter implements DeliveryChannelProvider {

    /** IN_APP 沿用历史哈希末段，保证既有 actionKey 不变。 */
    static final DeliveryChannel IN_APP = new DeliveryChannel(
            NotificationDeliveryProperties.IN_APP,
            InAppNotificationHandler.HANDLER_KEY,
            ActionExecutionMode.LOCAL_TRANSACTIONAL,
            InAppNotificationHandler.HANDLER_KEY);

    static final DeliveryChannel FEISHU = new DeliveryChannel(
            NotificationDeliveryProperties.FEISHU,
            FeishuImNotificationHandler.HANDLER_KEY,
            ActionExecutionMode.EXTERNAL,
            NotificationDeliveryProperties.FEISHU);

    private final List<DeliveryChannel> channels;

    public NotificationDeliveryChannelAdapter(NotificationDeliveryProperties properties) {
        this.channels = resolve(properties.getDeliveryChannels());
    }

    static List<DeliveryChannel> resolve(List<String> channelKeys) {
        NotificationDeliveryProperties.validate(channelKeys);
        return channelKeys.stream()
                .map(key -> NotificationDeliveryProperties.IN_APP.equals(key) ? IN_APP : FEISHU)
                .toList();
    }

    @Override
    public List<DeliveryChannel> enabledChannels() {
        return channels;
    }
}
