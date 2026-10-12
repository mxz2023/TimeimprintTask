package cn.net.mxz.timeimprint.task.common.wake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InboxWakeTest {

    @Test
    void wakeReachesCurrentListenerAndDropsAfterClose() {
        AtomicInteger seen = new AtomicInteger();
        InboxWake.Subscription subscription = InboxWake.listen(recipient -> {
            assertEquals("tenant", recipient.tenantId());
            assertEquals("USER", recipient.recipientType());
            assertEquals("actor", recipient.recipientId());
            seen.incrementAndGet();
        });
        InboxWake.wake("tenant", "USER", "actor");
        subscription.close();
        InboxWake.wake("tenant", "USER", "actor");
        assertEquals(1, seen.get());
    }

    @Test
    void listenerFailureDoesNotEscape() {
        InboxWake.Subscription subscription = InboxWake.listen(recipient -> {
            throw new IllegalStateException("client gone");
        });
        try {
            InboxWake.wake("tenant", "USER", "actor");
            assertTrue(true);
        } finally {
            subscription.close();
        }
    }
}
