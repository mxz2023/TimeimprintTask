package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.fixture.WebhookActionFixture;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import org.junit.jupiter.api.Test;

/** A33: handler timeout within LEASE_SECONDS − ACTION_LEASE_SAFETY_SECONDS (default 30−5). */
class A33HandlerLeaseMysqlIT {

    @Test
    void webhookFixtureTimeoutWithinLeaseSafety() {
        int maxAllowed = 30 - PlatformLimits.ACTION_LEASE_SAFETY_SECONDS;
        assertTrue(WebhookActionFixture.HANDLER_KEY.length() > 0);
        WebhookActionFixture fixture = new WebhookActionFixture();
        assertTrue(fixture.timeoutSeconds() <= maxAllowed);
        assertTrue(fixture.timeoutSeconds() >= 1);
    }
}
