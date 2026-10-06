package cn.net.mxz.timeimprint.task.boot.configuration.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cn.net.mxz.timeimprint.task.adapter.feishu.callback.FeishuCardActionVerifier;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuCardActionBridge;
import cn.net.mxz.timeimprint.task.gateway.callback.gateway.FeishuInboundSettings;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration.FeishuNotificationProperties;
import cn.net.mxz.timeimprint.task.web.callback.controller.FeishuCardActionController;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** 默认不装配任何入站 Bean；FEISHU 且 verification-token 非空时才装配验签器、配置视图与入站桥与回调 Controller。 */
class FeishuInboundConfigurationContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    BindingSupport.class,
                    FeishuNotificationProperties.class,
                    FeishuInboundConfiguration.class,
                    FeishuCardActionController.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(TaskGateway.class, () -> mock(TaskGateway.class))
            .withBean(BusinessClock.class, () -> () -> Instant.parse("2026-10-06T02:00:00Z"));

    @Test
    void defaultAssemblesNoInboundBeansAndNoController() {
        runner.withPropertyValues("timeimprint.notification.delivery-channels[0]=IN_APP")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(0, ctx.getBeansOfType(FeishuCardActionBridge.class).size());
                    assertEquals(0, ctx.getBeansOfType(FeishuCardActionVerifier.class).size());
                    assertEquals(0, ctx.getBeansOfType(FeishuCardActionController.class).size());
                });
    }

    @Test
    void feishuChannelWithoutVerificationTokenAssemblesNothing() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU",
                        "timeimprint.notification.feishu.verification-token=")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(0, ctx.getBeansOfType(FeishuCardActionBridge.class).size());
                    assertEquals(0, ctx.getBeansOfType(FeishuCardActionController.class).size());
                });
    }

    @Test
    void feishuChannelWithTokenAssemblesBridgeControllerAndBoundSettings() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU",
                        "timeimprint.notification.feishu.verification-token=tok",
                        "timeimprint.notification.feishu.recipient-map.local-actor=ou_1")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(1, ctx.getBeansOfType(FeishuCardActionBridge.class).size());
                    assertEquals(1, ctx.getBeansOfType(FeishuCardActionController.class).size());
                    assertEquals(Set.of("local-actor"), ctx.getBean(FeishuInboundSettings.class).platformUsersFor("ou_1"));
                    byte[] ok = "{\"header\":{\"token\":\"tok\"}}".getBytes(StandardCharsets.UTF_8);
                    byte[] bad = "{\"header\":{\"token\":\"nope\"}}".getBytes(StandardCharsets.UTF_8);
                    FeishuCardActionVerifier verifier = ctx.getBean(FeishuCardActionVerifier.class);
                    assertTrue(verifier.verify(null, null, null, ok));
                    assertTrue(!verifier.verify(null, null, null, bad));
                });
    }

    @Test
    void encryptKeyEnablesSignatureRequirement() {
        runner.withPropertyValues(
                        "timeimprint.notification.delivery-channels[0]=IN_APP",
                        "timeimprint.notification.delivery-channels[1]=FEISHU",
                        "timeimprint.notification.feishu.verification-token=tok",
                        "timeimprint.notification.feishu.encrypt-key=k")
                .run(ctx -> {
                    byte[] ok = "{\"header\":{\"token\":\"tok\"}}".getBytes(StandardCharsets.UTF_8);
                    assertTrue(!ctx.getBean(FeishuCardActionVerifier.class).verify(null, null, null, ok));
                });
    }

    @EnableConfigurationProperties
    static class BindingSupport {}
}
