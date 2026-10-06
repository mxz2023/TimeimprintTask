package cn.net.mxz.timeimprint.task.boot.configuration.feishu;

import cn.net.mxz.timeimprint.task.adapter.feishu.callback.DefaultFeishuCardActionVerifier;
import cn.net.mxz.timeimprint.task.adapter.feishu.callback.FeishuCardActionVerifier;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuCardActionBridge;
import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuInboundSettings;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration.FeishuNotificationProperties;
import cn.net.mxz.timeimprint.task.web.callback.configuration.FeishuInboundEnabledCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 飞书入站（card.action.trigger）组合根装配：把 notification 拥有的飞书配置桥接给 gateway 的入站桥，
 * 使 web/gateway 无需依赖 notification 模块。仅当 delivery-channels 含 FEISHU 且 verification-token 非空时生效；
 * 默认配置下不创建任何入站 Bean，回调路径不存在。
 */
@Configuration
@Conditional(FeishuInboundEnabledCondition.class)
public class FeishuInboundConfiguration {

    @Bean
    FeishuCardActionVerifier feishuCardActionVerifier(FeishuNotificationProperties properties, JsonMapper mapper) {
        return new DefaultFeishuCardActionVerifier(
                properties.getVerificationToken(), properties.getEncryptKey(), mapper);
    }

    @Bean
    FeishuInboundSettings feishuInboundSettings(FeishuNotificationProperties properties) {
        return new FeishuInboundSettings(properties.getRecipientMap());
    }

    @Bean
    FeishuCardActionBridge feishuCardActionBridge(
            FeishuCardActionVerifier verifier,
            FeishuInboundSettings settings,
            TaskGateway gateway,
            BusinessClock clock,
            JsonMapper mapper) {
        return new FeishuCardActionBridge(verifier, settings, gateway, clock, mapper);
    }
}
