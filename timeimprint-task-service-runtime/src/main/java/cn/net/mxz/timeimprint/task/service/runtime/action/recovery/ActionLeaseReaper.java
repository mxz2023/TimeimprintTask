package cn.net.mxz.timeimprint.task.service.runtime.action.recovery;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovers expired Action RUNNING leases using DB UTC ({@code lease_until &lt; UTC_TIMESTAMP()}).
 *
 * <p>EXTERNAL with effectStartedAt → UNKNOWN (no auto-retry). Otherwise → RETRY_WAIT/DEAD.
 */
@Component
public class ActionLeaseReaper {

    private static final Logger log = LoggerFactory.getLogger(ActionLeaseReaper.class);
    private static final int BATCH = 20;

    private final ActionJobExecutionPort actionPort;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public ActionLeaseReaper(
            ActionJobExecutionPort actionPort, TransactionBoundary tx, BusinessClock clock) {
        this.actionPort = actionPort;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 7000)
    public void pollAndRecover() {
        try {
            recoverBatch();
        } catch (Exception e) {
            log.warn("Action lease reaper: {}", e.getMessage());
        }
    }

    /** Visible for IT: recover up to one batch of expired RUNNING leases. */
    public int recoverBatch() {
        List<Long> ids = actionPort.listExpiredRunningIds(BATCH);
        int recovered = 0;
        for (Long id : ids) {
            Boolean ok = tx.execute(() -> actionPort.recoverExpiredLease(id, clock.nowUtcSeconds()));
            if (Boolean.TRUE.equals(ok)) {
                recovered++;
            }
        }
        return recovered;
    }
}
