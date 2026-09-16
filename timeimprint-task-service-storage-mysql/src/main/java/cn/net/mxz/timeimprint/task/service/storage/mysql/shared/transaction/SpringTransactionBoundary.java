package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.transaction;

import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring-backed transaction boundary with full-TX retry for deadlock / lock-wait (A15 / 06).
 *
 * <p>Retries only reopen a complete transaction (max 3 attempts). Callers must keep irreversible
 * EXTERNAL side effects outside {@link #execute}; otherwise a retry would replay them.
 */
@Component
public class SpringTransactionBoundary implements TransactionBoundary {

    static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactionTemplate;
    /** Test-visible: increments once per TX attempt (including successful). */
    private final AtomicInteger attemptCounter = new AtomicInteger();

    public SpringTransactionBoundary(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(PlatformLimits.BUSINESS_TX_TIMEOUT_SECONDS);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            attemptCounter.incrementAndGet();
            try {
                return transactionTemplate.execute(status -> work.get());
            } catch (RuntimeException ex) {
                if (!isRetryableLockFailure(ex) || attempt == MAX_ATTEMPTS) {
                    if (isRetryableLockFailure(ex) && attempt == MAX_ATTEMPTS) {
                        throw new ApplicationException(
                                "RETRY_LATER", "lock retry exhausted after " + MAX_ATTEMPTS + " attempts", ex);
                    }
                    throw ex;
                }
                last = ex;
            }
        }
        throw last != null
                ? last
                : new ApplicationException("RETRY_LATER", "lock retry exhausted");
    }

    @Override
    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    /** Visible for IT. */
    public int attemptCount() {
        return attemptCounter.get();
    }

    /** Visible for IT. */
    public void resetAttemptCount() {
        attemptCounter.set(0);
    }

    /** Visible for IT and diagnostics. */
    public static boolean isRetryableLockFailure(Throwable error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof DeadlockLoserDataAccessException
                    || t instanceof CannotAcquireLockException) {
                return true;
            }
            if (t instanceof TransientDataAccessException
                    && (t.getMessage() != null
                            && (t.getMessage().contains("Deadlock")
                                    || t.getMessage().contains("Lock wait timeout")))) {
                return true;
            }
            if (t instanceof UncategorizedSQLException use && use.getSQLException() != null) {
                if (isMysqlLockSql(use.getSQLException())) {
                    return true;
                }
            }
            if (t instanceof SQLException sql && isMysqlLockSql(sql)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean isMysqlLockSql(SQLException sql) {
        int code = sql.getErrorCode();
        // 1213 deadlock; 1205 lock wait timeout
        if (code == 1213 || code == 1205) {
            return true;
        }
        String state = sql.getSQLState();
        return "40001".equals(state);
    }
}
