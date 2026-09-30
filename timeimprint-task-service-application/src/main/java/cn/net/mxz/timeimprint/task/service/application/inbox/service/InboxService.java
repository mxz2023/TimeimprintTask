package cn.net.mxz.timeimprint.task.service.application.inbox.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.inbox.model.InboxRecord;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.util.List;
import org.springframework.stereotype.Service;

/** E10-E13: Inbox query and mark-read. */
@Service
public class InboxService {

    private final InboxRepository inboxRepository;
    private final ActorContextProvider actorContextProvider;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public InboxService(
            InboxRepository inboxRepository,
            ActorContextProvider actorContextProvider,
            TransactionBoundary tx,
            BusinessClock clock) {
        this.inboxRepository = inboxRepository;
        this.actorContextProvider = actorContextProvider;
        this.tx = tx;
        this.clock = clock;
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
        return inboxRepository.countUnread(actor.tenantKey(), actor.principalType(), actor.principalId());
    }

    /** E13: Mark-read. */
    public InboxRecord markRead(long inboxId) {
        var actor = actorContextProvider.requireCurrentActor();
        return tx.execute(() -> {
            inboxRepository.markReadIfUnread(inboxId, clock.nowUtcSeconds());
            return inboxRepository
                    .findByIdForRecipient(inboxId, actor.tenantKey(), actor.principalType(), actor.principalId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
        });
    }
}
