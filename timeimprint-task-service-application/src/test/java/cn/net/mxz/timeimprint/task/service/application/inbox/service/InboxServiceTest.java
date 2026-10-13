package cn.net.mxz.timeimprint.task.service.application.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InboxService public method surface for P02 refactor safety. */
class InboxServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("get/1",
                "latestInboxId/3",
                "list/2",
                "markRead/1",
                "unreadCount/0",
                "unreadCountFor/3"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void firstReadWakesRecipientAndRepeatReadDoesNot() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<InboxWake.Recipient> seen = new AtomicReference<>();
        InboxWake.Subscription subscription = InboxWake.listen(seen::set);
        try {
            InboxService service = service(reads);
            service.markRead(8L);
            assertEquals("actor", seen.get().recipientId());
            seen.set(null);
            service.markRead(8L);
            assertNull(seen.get());
            assertEquals(2, reads.get());
            assertEquals(2, service.unreadCountFor("tenant", "USER", "actor"));
            assertEquals(8L, service.latestInboxId("tenant", "USER", "actor"));
        } finally {
            subscription.close();
        }
    }

    private static InboxService service(AtomicInteger reads) {
        InboxRepository repository = new InboxRepository() {
            @Override
            public List<InboxRecord> listForRecipient(
                    String tenantId, String recipientType, String recipientId, boolean unreadOnly, int limit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<InboxRecord> findById(long inboxId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<InboxRecord> findByIdForRecipient(
                    long inboxId, String tenantId, String recipientType, String recipientId) {
                return Optional.of(record());
            }

            @Override
            public int countUnread(String tenantId, String recipientType, String recipientId) {
                return 2;
            }

            @Override
            public Long maxInboxId(String tenantId, String recipientType, String recipientId) {
                return 8L;
            }

            @Override
            public boolean markReadIfUnread(long inboxId, Instant readAt) {
                return reads.getAndIncrement() == 0;
            }
        };
        ActorContextProvider actors = new ActorContextProvider() {
            @Override
            public Optional<ActorContext> currentActor() {
                return Optional.of(new ActorContext("USER", "actor", "tenant"));
            }

            @Override
            public ActorContext requireCurrentActor() {
                return currentActor().orElseThrow();
            }
        };
        TransactionBoundary tx = new TransactionBoundary() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> work) {
                return work.get();
            }

            @Override
            public void execute(Runnable work) {
                work.run();
            }
        };
        BusinessClock clock = () -> Instant.parse("2026-10-12T14:00:00Z");
        return new InboxService(repository, actors, tx, clock);
    }

    private static InboxRecord record() {
        return new InboxRecord(8L, "tenant", 1L, 2L, 3L, 4L, "reminder", "REMIND", "title", "body",
                "USER", "actor", Instant.parse("2026-10-12T14:00:00Z"), Instant.parse("2026-10-12T13:00:00Z"));
    }
}
