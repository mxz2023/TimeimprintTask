package cn.net.mxz.timeimprint.task.web.callback.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

class FeishuInboundEnabledConditionTest {

    private static boolean matches(MockEnvironment env) {
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return new FeishuInboundEnabledCondition().matches(ctx, null);
    }

    private static MockEnvironment env() {
        return new MockEnvironment();
    }

    @Test
    void disabledByDefaultAndWhenFeishuNotInChannels() {
        assertFalse(matches(env()));
        assertFalse(matches(env().withProperty("timeimprint.notification.delivery-channels[0]", "IN_APP")
                .withProperty("timeimprint.notification.feishu.verification-token", "tok")));
    }

    @Test
    void requiresNonBlankVerificationToken() {
        MockEnvironment channels = env().withProperty("timeimprint.notification.delivery-channels[0]", "IN_APP")
                .withProperty("timeimprint.notification.delivery-channels[1]", "FEISHU");
        assertFalse(matches(channels));
        assertFalse(matches(channels.withProperty("timeimprint.notification.feishu.verification-token", "  ")));
    }

    @Test
    void enabledWhenFeishuChannelAndTokenPresent() {
        assertTrue(matches(env().withProperty("timeimprint.notification.delivery-channels[0]", "IN_APP")
                .withProperty("timeimprint.notification.delivery-channels[1]", "FEISHU")
                .withProperty("timeimprint.notification.feishu.verification-token", "tok")));
    }
}
