package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration;

import cn.net.mxz.timeimprint.task.adapter.feishu.auth.CachingFeishuTokenProvider;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuMessageClient;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.HttpFeishuMessageClient;
import cn.net.mxz.timeimprint.task.service.capability.notification.notification.configuration.NotificationDeliveryProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import tools.jackson.databind.json.JsonMapper;

/**
 * 仅当 {@code delivery-channels} 含 FEISHU 时装配真实飞书出站客户端；默认仅 IN_APP 时不创建任何飞书 Bean，
 * 也不要求凭据。启用后凭据缺失会让应用启动失败（03 §3.1）。
 */
@Configuration
@Conditional(FeishuOutboundConfiguration.FeishuEnabledCondition.class)
public class FeishuOutboundConfiguration {

    @Bean
    FeishuMessageClient feishuMessageClient(FeishuNotificationProperties properties) {
        properties.validateForOutbound(FeishuNotificationProperties.OUTBOUND_BUDGET_SECONDS);
        var adapterProps = properties.adapterProperties();
        // 平台自行管理重试（Action 状态机）：JDK HttpClient 无隐藏重试，仅设置连接超时。
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(adapterProps.timeoutSeconds()))
                .build();
        JsonMapper mapper = JsonMapper.builder().build();
        var tokens = new CachingFeishuTokenProvider(
                adapterProps, properties.credentials(), http, mapper, Clock.systemUTC());
        return new HttpFeishuMessageClient(adapterProps, tokens, http, mapper);
    }

    /** 读取原始环境配置判断 FEISHU 是否启用，避免依赖其它 Bean 初始化顺序。 */
    static final class FeishuEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            List<String> channels = Binder.get(context.getEnvironment())
                    .bind("timeimprint.notification.delivery-channels", Bindable.listOf(String.class))
                    .orElse(List.of());
            return channels.contains(NotificationDeliveryProperties.FEISHU);
        }
    }
}
