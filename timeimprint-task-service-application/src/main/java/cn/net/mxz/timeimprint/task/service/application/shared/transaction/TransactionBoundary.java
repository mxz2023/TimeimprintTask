package cn.net.mxz.timeimprint.task.service.application.shared.transaction;

import java.util.function.Supplier;

/** 业务写事务边界；实现由 runtime/storage 提供，application 只依赖端口。 */
public interface TransactionBoundary {

    <T> T execute(Supplier<T> work);

    void execute(Runnable work);

    /**
     * 当前写事务提交后执行。没有活动事务时立即执行。
     * 这里的失败不能回滚已经提交的业务写入。
     */
    default void afterCommit(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 提交后的通知失败保持业务结果。
        }
    }
}
