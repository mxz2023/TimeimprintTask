package cn.net.mxz.timeimprint.task.service.capability.notification.notification.configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 {@code timeimprint.notification.delivery-channels}。
 *
 * <p>启动时 fail-fast：列表非空、无重复、仅允许 IN_APP / FEISHU，且必须含 IN_APP。
 * FEISHU 所需凭据校验由 P05 T03 在真正启用飞书出站时补充；仅 IN_APP 时不需要任何飞书配置。
 */
@Component
@ConfigurationProperties(prefix = "timeimprint.notification")
public class NotificationDeliveryProperties implements InitializingBean {

    public static final String IN_APP = "IN_APP";
    public static final String FEISHU = "FEISHU";
    public static final Set<String> SUPPORTED = Set.of(IN_APP, FEISHU);

    private List<String> deliveryChannels = new ArrayList<>(List.of(IN_APP));

    public List<String> getDeliveryChannels() {
        return deliveryChannels;
    }

    public void setDeliveryChannels(List<String> deliveryChannels) {
        this.deliveryChannels = deliveryChannels == null ? new ArrayList<>() : new ArrayList<>(deliveryChannels);
    }

    @Override
    public void afterPropertiesSet() {
        validate(deliveryChannels);
    }

    /** 校验渠道列表；非法时抛出 {@link IllegalStateException}，使应用无法就绪。 */
    public static void validate(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalStateException("timeimprint.notification.delivery-channels must not be empty");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String ch : channels) {
            if (ch == null || !SUPPORTED.contains(ch)) {
                throw new IllegalStateException(
                        "unsupported delivery channel '" + ch + "', allowed " + SUPPORTED);
            }
            if (!seen.add(ch)) {
                throw new IllegalStateException("duplicate delivery channel '" + ch + "'");
            }
        }
        if (!seen.contains(IN_APP)) {
            throw new IllegalStateException("timeimprint.notification.delivery-channels must contain IN_APP");
        }
    }
}
