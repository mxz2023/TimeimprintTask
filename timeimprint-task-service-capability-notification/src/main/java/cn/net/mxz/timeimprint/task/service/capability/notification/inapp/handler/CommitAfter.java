package cn.net.mxz.timeimprint.task.service.capability.notification.inapp.handler;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在当前事务提交后执行。没有事务时立即执行。失败不向外抛。 */
final class CommitAfter {

    private CommitAfter() {}

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(action);
                }
            });
            return;
        }
        safely(action);
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 收件已经提交时，唤醒失败不能回滚。
        }
    }
}
