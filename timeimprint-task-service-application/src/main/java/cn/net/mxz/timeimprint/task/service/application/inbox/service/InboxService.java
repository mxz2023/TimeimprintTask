package cn.net.mxz.timeimprint.task.service.application.inbox.service;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.wake.InboxWake;
import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository.SoftDeleteOutcome;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

/** E10–E13 查询与已读，以及 E31/E32 软删。 */
@Service
public class InboxService {

    private static final String EMPTY_DIGEST = "{}";

    public record DeletedInbox(long inboxId, Instant deletedAt) {}

    public record ClearedRead(int clearedCount) {}

    private final InboxRepository inboxRepository;
    private final ActorContextProvider actorContextProvider;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final CommandDedupRepository commandDedupRepository;
    private final JsonMapper objectMapper;

    public InboxService(
            InboxRepository inboxRepository,
            ActorContextProvider actorContextProvider,
            TransactionBoundary tx,
            BusinessClock clock,
            CommandDedupRepository commandDedupRepository,
            JsonMapper objectMapper) {
        this.inboxRepository = inboxRepository;
        this.actorContextProvider = actorContextProvider;
        this.tx = tx;
        this.clock = clock;
        this.commandDedupRepository = commandDedupRepository;
        this.objectMapper = objectMapper;
    }

    /** E10: List inbox items. */
    public List<InboxRecord> list(boolean unreadOnly, int limit) {
        var actor = actorContextProvider.requireCurrentActor();
        return inboxRepository.listForRecipient(
                actor.tenantKey(), actor.principalType(), actor.principalId(),
                unreadOnly, Math.min(limit, 100));
    }

    /** E11: Get single inbox item. */
    public InboxRecord get(long inboxId) {
        var actor = actorContextProvider.requireCurrentActor();
        return inboxRepository
                .findByIdForRecipient(inboxId, actor.tenantKey(), actor.principalType(), actor.principalId())
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
    }

    /** E12: Unread count. */
    public int unreadCount() {
        var actor = actorContextProvider.requireCurrentActor();
        return unreadCountFor(actor.tenantKey(), actor.principalType(), actor.principalId());
    }

    /** 指定接收人的未读数。事件流在请求线程之外调用，不能再读当前线程身份。 */
    public int unreadCountFor(String tenantId, String recipientType, String recipientId) {
        return inboxRepository.countUnread(tenantId, recipientType, recipientId);
    }

    /** 指定接收人当前最大可见收件编号。没有可见收件时返回 null。 */
    public Long latestInboxId(String tenantId, String recipientType, String recipientId) {
        return inboxRepository.maxInboxId(tenantId, recipientType, recipientId);
    }

    /** E13: Mark-read. */
    public InboxRecord markRead(long inboxId) {
        var actor = actorContextProvider.requireCurrentActor();
        return tx.execute(() -> {
            boolean firstRead = inboxRepository.markReadIfUnread(inboxId, clock.nowUtcSeconds());
            InboxRecord record = inboxRepository
                    .findByIdForRecipient(inboxId, actor.tenantKey(), actor.principalType(), actor.principalId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
            if (firstRead) {
                tx.afterCommit(() -> InboxWake.wake(
                        actor.tenantKey(), actor.principalType(), actor.principalId()));
            }
            return record;
        });
    }

    /** E31: 软删一条收件。重复删除保留首次 deletedAt。 */
    public DeletedInbox delete(long inboxId, String requestId) {
        var actor = actorContextProvider.requireCurrentActor();
        String operation = "POST /api/v1/inbox/" + inboxId + "/commands/delete";
        byte[] hash = Sha256.digestUtf8(EMPTY_DIGEST);
        return tx.execute(() -> {
            DeletedInbox replay = completedDelete(
                    actor.tenantKey(), actor.principalId(), operation, requestId, hash);
            if (replay != null) {
                return replay;
            }
            Optional<String> raced = begin(actor.tenantKey(), actor.principalId(), operation, requestId, hash);
            if (raced.isPresent()) {
                return deletedFrom(raced.get());
            }
            SoftDeleteOutcome outcome = inboxRepository
                    .softDelete(
                            inboxId,
                            actor.tenantKey(),
                            actor.principalType(),
                            actor.principalId(),
                            clock.nowUtcSeconds())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
            String json = objectMapper.writeValueAsString(
                    Map.of("inboxId", inboxId, "deletedAt", outcome.deletedAt().toString()));
            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    operation,
                    requestId,
                    "OK",
                    "INBOX",
                    String.valueOf(inboxId),
                    null,
                    json);
            if (outcome.newlyDeleted()) {
                tx.afterCommit(() -> InboxWake.wake(
                        actor.tenantKey(), actor.principalType(), actor.principalId()));
            }
            return new DeletedInbox(inboxId, outcome.deletedAt());
        });
    }

    /** E32: 软删当前接收人的已读收件。 */
    public ClearedRead clearRead(String requestId) {
        var actor = actorContextProvider.requireCurrentActor();
        String operation = "POST /api/v1/inbox/commands/clear-read";
        byte[] hash = Sha256.digestUtf8(EMPTY_DIGEST);
        return tx.execute(() -> {
            ClearedRead replay = completedClear(
                    actor.tenantKey(), actor.principalId(), operation, requestId, hash);
            if (replay != null) {
                return replay;
            }
            Optional<String> raced = begin(actor.tenantKey(), actor.principalId(), operation, requestId, hash);
            if (raced.isPresent()) {
                return new ClearedRead(objectMapper.readTree(raced.get()).path("clearedCount").asInt());
            }
            int cleared = inboxRepository.clearRead(
                    actor.tenantKey(), actor.principalType(), actor.principalId(), clock.nowUtcSeconds());
            String json = objectMapper.writeValueAsString(Map.of("clearedCount", cleared));
            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    operation,
                    requestId,
                    "OK",
                    "INBOX",
                    "clear-read",
                    null,
                    json);
            if (cleared > 0) {
                tx.afterCommit(() -> InboxWake.wake(
                        actor.tenantKey(), actor.principalType(), actor.principalId()));
            }
            return new ClearedRead(cleared);
        });
    }

    private DeletedInbox completedDelete(
            String tenantId, String actorId, String operation, String requestId, byte[] hash) {
        var existing = commandDedupRepository.find(tenantId, actorId, operation, requestId);
        if (existing.isEmpty()) {
            return null;
        }
        assertSameRequestHash(existing.get().requestHash(), hash);
        if ("COMPLETED".equals(existing.get().processStatus())) {
            return deletedFrom(existing.get().responseJson());
        }
        throw new ApplicationException("RETRY_LATER", "dedup in progress");
    }

    private ClearedRead completedClear(
            String tenantId, String actorId, String operation, String requestId, byte[] hash) {
        var existing = commandDedupRepository.find(tenantId, actorId, operation, requestId);
        if (existing.isEmpty()) {
            return null;
        }
        assertSameRequestHash(existing.get().requestHash(), hash);
        if ("COMPLETED".equals(existing.get().processStatus())) {
            JsonNode node = objectMapper.readTree(existing.get().responseJson());
            return new ClearedRead(node.path("clearedCount").asInt());
        }
        throw new ApplicationException("RETRY_LATER", "dedup in progress");
    }

    /** 空表示本调用拿到 PROCESSING 锁；非空是并发提交后已经完成的响应。 */
    private Optional<String> begin(String tenantId, String actorId, String operation, String requestId, byte[] hash) {
        if (commandDedupRepository.tryBegin(tenantId, actorId, operation, requestId, hash)) {
            return Optional.empty();
        }
        var again = commandDedupRepository.find(tenantId, actorId, operation, requestId);
        if (again.isPresent()) {
            assertSameRequestHash(again.get().requestHash(), hash);
            if ("COMPLETED".equals(again.get().processStatus())) {
                String json = again.get().responseJson();
                if (json == null) {
                    throw new ApplicationException("RETRY_LATER", "dedup completed without body");
                }
                return Optional.of(json);
            }
        }
        throw new ApplicationException("RETRY_LATER", "dedup in progress");
    }

    private DeletedInbox deletedFrom(String json) {
        JsonNode node = objectMapper.readTree(json);
        return new DeletedInbox(node.path("inboxId").asLong(), Instant.parse(node.path("deletedAt").asText()));
    }

    private static void assertSameRequestHash(byte[] stored, byte[] incoming) {
        if (stored != null && !Arrays.equals(stored, incoming)) {
            throw new ApplicationException("IDEMPOTENCY_CONFLICT", "same requestId different payload");
        }
    }
}
