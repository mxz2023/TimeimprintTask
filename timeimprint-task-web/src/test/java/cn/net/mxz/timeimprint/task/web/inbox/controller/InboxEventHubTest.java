package cn.net.mxz.timeimprint.task.web.inbox.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.web.inbox.controller.InboxEventHub.Cursor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InboxEventHubTest {

    @Test
    void frameOmitsTitleAndUsesNullWhenEmpty() {
        assertEquals(
                "{\"unreadCount\":1,\"latestInboxId\":10,\"asOf\":\"2026-10-12T14:00:00Z\"}",
                InboxEventHub.frame(1, 10L, "2026-10-12T14:00:00Z"));
        assertTrue(InboxEventHub.frame(0, null, "2026-10-12T14:00:00Z").contains("\"latestInboxId\":null"));
        assertEquals(25_000L, InboxEventHub.HEARTBEAT_MILLIS);
        assertEquals(5_000L, InboxEventHub.POLL_MILLIS);
        assertEquals(30L * 60L * 1000L, InboxEventHub.MAX_AGE_MILLIS);
    }

    @Test
    void wakeReadsOnlyTheMatchingRecipient() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        var scheduler = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "inbox-events-test");
            thread.setDaemon(true);
            return thread;
        });
        InboxEventHub hub = new InboxEventHub(
                (tenantId, recipientType, recipientId) -> {
                    reads.incrementAndGet();
                    latch.countDown();
                    return new Cursor(1, 4L, "2026-10-12T14:00:00Z");
                },
                scheduler,
                60_000L,
                60_000L,
                InboxEventHub.MAX_AGE_MILLIS);
        try {
            hub.open(new ActorContext("USER", "actor", "tenant"));
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            int afterOpen = reads.get();
            InboxWake.wake("other", "USER", "actor");
            InboxWake.wake("tenant", "USER", "actor");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (reads.get() == afterOpen && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(reads.get() > afterOpen);
        } finally {
            hub.close();
        }
    }
}
