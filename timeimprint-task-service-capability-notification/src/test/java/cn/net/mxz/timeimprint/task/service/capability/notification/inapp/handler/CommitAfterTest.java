package cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CommitAfterTest {

    @Test
    void runsImmediatelyWhenNoTransactionIsOpen() {
        AtomicBoolean ran = new AtomicBoolean();
        CommitAfter.run(() -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void waitsUntilCommitAndSwallowsListenerFailure() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            AtomicBoolean ran = new AtomicBoolean();
            CommitAfter.run(() -> {
                throw new IllegalStateException("wake failed");
            });
            CommitAfter.run(() -> ran.set(true));
            assertFalse(ran.get());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertTrue(ran.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
