package cn.net.mxz.timeimprint.task.web.callback.configuration;

import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 飞书入站回调装配条件（03 §3.1）：{@code timeimprint.notification.delivery-channels} 含 {@code FEISHU}，
 * 且 {@code timeimprint.notification.feishu.verification-token} 非空。
 *
 * <p>直接读环境配置而不依赖其它 Bean，避免装配顺序问题；回调 Controller 与组合根里的入站桥共用本条件，
 * 默认配置（仅 IN_APP）下回调路径不存在（404）。
 */
public final class FeishuInboundEnabledCondition implements Condition {

    public static final String CHANNELS_KEY = "timeimprint.notification.delivery-channels";
    public static final String TOKEN_KEY = "timeimprint.notification.feishu.verification-token";
    public static final String FEISHU_CHANNEL = "FEISHU";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Binder binder = Binder.get(context.getEnvironment());
        List<String> channels = binder.bind(CHANNELS_KEY, Bindable.listOf(String.class)).orElse(List.of());
        if (!channels.contains(FEISHU_CHANNEL)) {
            return false;
        }
        String token = binder.bind(TOKEN_KEY, Bindable.of(String.class)).orElse("");
        return !token.isBlank();
    }
}
