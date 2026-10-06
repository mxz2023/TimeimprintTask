package cn.net.mxz.timeimprint.task.service.extension.action.spi;

import cn.net.mxz.timeimprint.task.service.extension.action.registry.DeliveryChannel;
import java.util.List;

/**
 * 通知投递渠道提供者：由 notification 能力按运行配置 {@code delivery-channels} 实现，
 * 平台据此把「接收人」展开为「接收人 × 渠道」的 Action。
 */
public interface DeliveryChannelProvider {

    /** 当前启用的渠道，顺序稳定、非空且至少含 IN_APP。 */
    List<DeliveryChannel> enabledChannels();
}
