package cn.net.mxz.timeimprint.task.service.application.shared.transaction;

import java.util.function.Supplier;

/** 业务写事务边界；实现由 runtime/storage 提供，application 只依赖端口。 */
public interface TransactionBoundary {

    <T> T execute(Supplier<T> work);

    void execute(Runnable work);
}
