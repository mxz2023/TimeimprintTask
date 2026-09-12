package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzInboxRecord;
import cn.net.mxz.timeimprint.task.service.application.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import java.util.List;
import org.springframework.stereotype.Service;

/** E10-E13: Inbox query and mark-read. */
@Service
public class MxzInboxService {

    private final InboxRepository inboxRepository;
    private final ActorContextProvider actorContextProvider;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public MxzInboxService(
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
    public List<MxzInboxRecord> list(boolean unreadOnly, int limit) {
        var actor = actorContextProvider.requireCurrentActor();
        return inboxRepository.listForRecipient(
                actor.tenantKey(), actor.principalType(), actor.principalId(),
                unreadOnly, Math.min(limit, 100));
    }

    /** E11: Get single inbox item. */
    public MxzInboxRecord get(long inboxId) {
        var actor = actorContextProvider.requireCurrentActor();
        return inboxRepository
                .findByIdForRecipient(inboxId, actor.tenantKey(), actor.principalType(), actor.principalId())
                .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
    }

    /** E12: Unread count. */
    public int unreadCount() {
        var actor = actorContextProvider.requireCurrentActor();
        return inboxRepository.countUnread(actor.tenantKey(), actor.principalType(), actor.principalId());
    }

    /** E13: Mark-read. */
    public MxzInboxRecord markRead(long inboxId) {
        var actor = actorContextProvider.requireCurrentActor();
        return tx.execute(() -> {
            inboxRepository.markReadIfUnread(inboxId, clock.nowUtcSeconds());
            return inboxRepository
                    .findByIdForRecipient(inboxId, actor.tenantKey(), actor.principalType(), actor.principalId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "inbox item not found"));
        });
    }
}
