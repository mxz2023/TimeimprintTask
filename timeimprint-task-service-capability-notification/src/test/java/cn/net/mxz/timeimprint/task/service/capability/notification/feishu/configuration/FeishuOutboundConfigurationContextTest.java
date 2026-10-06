package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuMessageClient;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler.FeishuImNotificationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 默认仅 IN_APP 不装配飞书也不要求凭据；启用 FEISHU 后必须凭据齐全才能启动。 */
class FeishuOutboundConfigurationContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    BindingSupport.class,
                    FeishuNotificationProperties.class,
                    FeishuOutboundConfiguration.class,
                    FeishuImNotificationHandler.class);

    @Test
    void defaultInAppOnlyAssemblesNoClientAndNeedsNoCredentials() {
        runner.withPropertyValues("timeimprint.notification.delivery-channels[0]=IN_APP")
                .run(ctx -> {
                    assertTrue(ctx.getStartupFailure() == null);
                    assertEquals(0, ctx.getBeansOfType(FeishuMessageClient.class).size());
                    assertNotNull(ctx.getBean(FeishuImNotificationHandler.class));
                });
    }

    @Test
    void noChannelPropertyAtAllAssemblesNoClient() {
        runner.run(ctx -> assertEquals(0, ctx.getBeansOfType(FeishuMessageClient.class).size()));
    }

    @Test
    void feishuEnabledWithoutCredentialsFailsStartup() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU")
                .run(ctx -> {
                    assertNotNull(ctx.getStartupFailure());
                    assertTrue(rootMessage(ctx.getStartupFailure()).contains("app-id"));
                });
    }

    @Test
    void feishuEnabledWithCredentialsAssemblesRealClientEvenWithEmptyRecipientMap() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU",
                        "timeimprint.notification.feishu.app-id=cli_x",
                        "timeimprint.notification.feishu.app-secret=s_x",
                        "timeimprint.notification.feishu.verification-token=vt_x")
                .run(ctx -> {
                    assertTrue(ctx.getStartupFailure() == null);
                    assertEquals(1, ctx.getBeansOfType(FeishuMessageClient.class).size());
                    assertNotNull(ctx.getBean(FeishuImNotificationHandler.class));
                });
    }

    @Test
    void feishuEnabledWithoutVerificationTokenFailsStartup() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU",
                        "timeimprint.notification.feishu.app-id=cli_x",
                        "timeimprint.notification.feishu.app-secret=s_x")
                .run(ctx -> {
                    assertNotNull(ctx.getStartupFailure());
                    assertTrue(rootMessage(ctx.getStartupFailure()).contains("verification-token"));
                });
    }

    /** 开启 {@code @ConfigurationProperties} 绑定（测试上下文不含完整自动配置）。 */
    @EnableConfigurationProperties
    static class BindingSupport {}

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return String.valueOf(c.getMessage());
    }
}
