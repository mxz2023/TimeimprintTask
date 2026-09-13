package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovers expired Signal RUNNING leases using DB UTC ({@code lease_until &lt; UTC_TIMESTAMP()}).
 *
 * <p>Clears old token; not exhausted → RETRY_WAIT; exhausted → DEAD.
 */
@Component
public class MxzSignalLeaseReaper {

    private static final Logger log = LoggerFactory.getLogger(MxzSignalLeaseReaper.class);
    private static final int BATCH = 20;

    private final TaskSignalRepository signalRepository;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public MxzSignalLeaseReaper(
            TaskSignalRepository signalRepository, TransactionBoundary tx, BusinessClock clock) {
        this.signalRepository = signalRepository;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6500)
    public void pollAndRecover() {
        try {
            recoverBatch();
        } catch (Exception e) {
            log.warn("Signal lease reaper: {}", e.getMessage());
        }
    }

    /** Visible for IT: recover up to one batch of expired RUNNING Signal leases. */
    public int recoverBatch() {
        List<Long> ids = signalRepository.listExpiredRunningIds(BATCH);
        int recovered = 0;
        for (Long id : ids) {
            Boolean ok = tx.execute(() -> signalRepository.recoverExpiredLease(id, clock.nowUtcSeconds()));
            if (Boolean.TRUE.equals(ok)) {
                recovered++;
            }
        }
        return recovered;
    }
}
