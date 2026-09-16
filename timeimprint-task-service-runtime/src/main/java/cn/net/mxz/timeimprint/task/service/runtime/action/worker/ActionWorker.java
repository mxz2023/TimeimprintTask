package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import cn.net.mxz.timeimprint.task.service.runtime.signal.worker.SignalWorker;

/**
 * Background worker for READY Action jobs: fair claim polling and LOCAL/EXTERNAL dispatch.
 *
 * <p>LOCAL_TRANSACTIONAL and EXTERNAL paths live in dedicated executors so each protocol can
 * change independently without touching the poller.
 */
@Component
public class ActionWorker {

    private static final Logger log = LoggerFactory.getLogger(ActionWorker.class);

    private final ActionJobExecutionPort actionPort;
    private final BusinessClock clock;
    private final RuntimeAdmission admission;
    private final ActionLocalExecutor localExecutor;
    private final ActionExternalExecutor externalExecutor;
    private final int claimBatchSize;

    public ActionWorker(
            ActionJobExecutionPort actionPort,
            BusinessClock clock,
            RuntimeAdmission admission,
            ActionLocalExecutor localExecutor,
            ActionExternalExecutor externalExecutor,
            @Value("${CLAIM_BATCH_SIZE:50}") int claimBatchSize) {
        this.actionPort = actionPort;
        this.clock = clock;
        this.admission = admission;
        this.localExecutor = localExecutor;
        this.externalExecutor = externalExecutor;
        this.claimBatchSize = SignalWorker.clampClaimBatch(claimBatchSize);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6000)
    public void pollAndExecute() {
        if (!admission.acceptingClaims()) {
            return;
        }
        try {
            for (Long actionJobId : selectFairDueIds()) {
                if (!admission.acceptingClaims()) {
                    return;
                }
                try {
                    executeAction(actionJobId);
                } catch (Exception e) {
                    log.warn("Action worker: error executing action {}: {}", actionJobId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Action worker: poll error: {}", e.getMessage());
        }
    }

    /** Visible for IT: one poll cycle; returns candidate count (≤ claim batch). */
    public int pollOnceForTests() {
        List<Long> ids = selectFairDueIds();
        for (Long actionJobId : ids) {
            executeAction(actionJobId);
        }
        return ids.size();
    }

    public int claimBatchSize() {
        return claimBatchSize;
    }

    /**
     * 06 §10 fairness: half the batch prefers newest due work; remainder drains oldest-first.
     * Total candidates ≤ {@code CLAIM_BATCH_SIZE} (clamped 1—100).
     */
    private List<Long> selectFairDueIds() {
        Instant now = clock.nowUtcSeconds();
        int freshShare = Math.max(1, claimBatchSize / 2);
        List<Long> fresh = actionPort.listReadyDueIdsNewestFirst(now, freshShare);
        java.util.LinkedHashSet<Long> merged = new java.util.LinkedHashSet<>(fresh);
        for (Long id : actionPort.listReadyDueIds(now, claimBatchSize)) {
            if (merged.size() >= claimBatchSize) {
                break;
            }
            merged.add(id);
        }
        return List.copyOf(merged);
    }

    /** Visible for IT: run one action through the same path as the poller. */
    public void executeAction(long actionJobId) {
        var peek = actionPort.findById(actionJobId);
        if (peek.isEmpty()) {
            return;
        }
        if ("EXTERNAL".equals(peek.get().executionMode())) {
            externalExecutor.execute(actionJobId);
        } else {
            localExecutor.execute(actionJobId, false);
        }
    }

    /**
     * IT hook for A10: write LOCAL effect then complete with a mismatched token so CAS fails and
     * the whole transaction (inbox + attempt + claim) rolls back.
     */
    public void executeLocalWithForcedCasFailure(long actionJobId) {
        localExecutor.execute(actionJobId, true);
    }
}
