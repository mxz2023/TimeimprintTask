package cn.net.mxz.timeimprint.task.service.application.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository.SoftDeleteOutcome;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Owner test: freeze InboxService public method surface for P02 refactor safety. */
class InboxServiceTest {

    private static final String REQUEST = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_2 = "22222222-2222-2222-2222-222222222222";

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InboxService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("clearRead/1",
                "delete/2",
                "get/1",
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
            InboxService service = service(reads, true);
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

    @Test
    void deleteWakesOnceAndKeepsFirstDeletedAt() {
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger wakes = new AtomicInteger();
        InboxWake.Subscription subscription = InboxWake.listen(recipient -> wakes.incrementAndGet());
        try {
            InboxService service = service(deletes, true);
            InboxService.DeletedInbox first = service.delete(8L, REQUEST);
            InboxService.DeletedInbox replay = service.delete(8L, REQUEST);
            InboxService.DeletedInbox again = service.delete(8L, REQUEST_2);
            assertEquals(first.deletedAt(), replay.deletedAt());
            assertEquals(first.deletedAt(), again.deletedAt());
            assertEquals(2, deletes.get());
            assertEquals(1, wakes.get());
        } finally {
            subscription.close();
        }
    }

    @Test
    void pushFailureDoesNotUndoDelete() {
        InboxWake.Subscription subscription = InboxWake.listen(recipient -> {
            throw new IllegalStateException("push failed");
        });
        try {
            InboxService service = service(new AtomicInteger(), true);
            InboxService.DeletedInbox deleted = service.delete(8L, REQUEST);
            assertEquals(8L, deleted.inboxId());
            assertEquals(Instant.parse("2026-10-18T10:54:00Z"), deleted.deletedAt());
        } finally {
            subscription.close();
        }
    }

    @Test
    void missingInboxDeleteIsNotFound() {
        InboxService service = service(new AtomicInteger(), false);
        ApplicationException ex = assertThrows(ApplicationException.class, () -> service.delete(8L, REQUEST));
        assertEquals("RESOURCE_NOT_FOUND", ex.errorCode());
    }

    @Test
    void clearReadWakesOnlyWhenRowsChange() {
        AtomicInteger wakes = new AtomicInteger();
        InboxWake.Subscription subscription = InboxWake.listen(recipient -> wakes.incrementAndGet());
        try {
            InboxService service = service(new AtomicInteger(), true);
            assertEquals(1, service.clearRead(REQUEST).clearedCount());
            assertEquals(1, service.clearRead(REQUEST).clearedCount());
            assertEquals(0, service.clearRead(REQUEST_2).clearedCount());
            assertEquals(1, wakes.get());
        } finally {
            subscription.close();
        }
    }

    private static InboxService service(AtomicInteger counter, boolean visible) {
        InboxRepository repository = new InboxRepository() {
            private Instant firstDeletedAt;

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
                return counter.getAndIncrement() == 0;
            }

            @Override
            public Optional<SoftDeleteOutcome> softDelete(
                    long inboxId, String tenantId, String recipientType, String recipientId, Instant deletedAt) {
                counter.incrementAndGet();
                if (!visible) {
                    return Optional.empty();
                }
                boolean newly = firstDeletedAt == null;
                if (newly) {
                    firstDeletedAt = deletedAt;
                }
                return Optional.of(new SoftDeleteOutcome(firstDeletedAt, newly));
            }

            @Override
            public int clearRead(String tenantId, String recipientType, String recipientId, Instant deletedAt) {
                return counter.getAndIncrement() == 0 ? 1 : 0;
            }

            @Override
            public int countUnreadByInstance(long instanceId) {
                return 0;
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
        BusinessClock clock = () -> Instant.parse("2026-10-18T10:54:00Z");
        return new InboxService(repository, actors, tx, clock, new MemoryDedup(), JsonMapper.builder().build());
    }

    private static InboxRecord record() {
        return new InboxRecord(8L, "tenant", 1L, 2L, 3L, 4L, "reminder", "REMIND", "title", "body",
                "USER", "actor", Instant.parse("2026-10-12T14:00:00Z"), Instant.parse("2026-10-12T13:00:00Z"));
    }

    private static final class MemoryDedup implements CommandDedupRepository {
        private final Map<String, CommandDedupSnapshot> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<String> findCompletedResponseJson(
                String tenantId, String actorId, String operation, String requestId) {
            return find(tenantId, actorId, operation, requestId)
                    .filter(row -> "COMPLETED".equals(row.processStatus()))
                    .map(CommandDedupSnapshot::responseJson);
        }

        @Override
        public Optional<CommandDedupSnapshot> find(
                String tenantId, String actorId, String operation, String requestId) {
            return Optional.ofNullable(rows.get(key(tenantId, actorId, operation, requestId)));
        }

        @Override
        public boolean tryBegin(
                String tenantId, String actorId, String operation, String requestId, byte[] requestHash) {
            return rows.putIfAbsent(
                            key(tenantId, actorId, operation, requestId),
                            new CommandDedupSnapshot("PROCESSING", requestHash, null))
                    == null;
        }

        @Override
        public void complete(
                String tenantId,
                String actorId,
                String operation,
                String requestId,
                String resultCode,
                String resourceType,
                String resourceId,
                Long resourceRevision,
                String responseJson) {
            CommandDedupSnapshot current = rows.get(key(tenantId, actorId, operation, requestId));
            rows.put(
                    key(tenantId, actorId, operation, requestId),
                    new CommandDedupSnapshot("COMPLETED", current.requestHash(), responseJson));
        }

        private static String key(String tenantId, String actorId, String operation, String requestId) {
            return tenantId + "|" + actorId + "|" + operation + "|" + requestId;
        }
    }
}
