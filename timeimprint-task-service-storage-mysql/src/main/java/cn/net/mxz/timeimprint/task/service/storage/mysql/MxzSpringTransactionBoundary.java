package cn.net.mxz.timeimprint.task.service.storage.mysql;

import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Spring-backed transaction boundary. */
@Component
public class MxzSpringTransactionBoundary implements TransactionBoundary {

    @Override
    @Transactional
    public <T> T execute(Supplier<T> work) {
        return work.get();
    }

    @Override
    @Transactional
    public void execute(Runnable work) {
        work.run();
    }
}
