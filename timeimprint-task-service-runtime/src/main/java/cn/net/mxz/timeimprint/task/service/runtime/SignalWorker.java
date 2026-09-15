package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.runtime.RuntimeAdmission;
import cn.net.mxz.timeimprint.task.service.application.service.SignalProcessingService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker: claims READY/RETRY_WAIT signals due for processing
 * and dispatches each to SignalProcessingService.
 *
 * Uses a simple poll-then-process pattern:
 * 1. List due signal IDs (no lock; lightweight).
 * 2. For each ID: SignalProcessingService handles its own transaction with
 *    findByIdForUpdate and proper token management.
 */
@Component
public class SignalWorker {

    private static final Logger log = LoggerFactory.getLogger(SignalWorker.class);

    private final TaskSignalRepository signalRepository;
    private final SignalProcessingService processingService;
    private final BusinessClock clock;
    private final RuntimeAdmission admission;
    private final int claimBatchSize;

    public SignalWorker(
            TaskSignalRepository signalRepository,
            SignalProcessingService processingService,
            BusinessClock clock,
            RuntimeAdmission admission,
            @Value("${CLAIM_BATCH_SIZE:50}") int claimBatchSize) {
        this.signalRepository = signalRepository;
        this.processingService = processingService;
        this.clock = clock;
        this.admission = admission;
        this.claimBatchSize = clampClaimBatch(claimBatchSize);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void pollAndProcess() {
        if (!admission.acceptingClaims()) {
            return;
        }
        try {
            List<Long> ids = signalRepository.listReadyDueIds(clock.nowUtcSeconds(), claimBatchSize);
            for (Long signalId : ids) {
                if (!admission.acceptingClaims()) {
                    return;
                }
                try {
                    processingService.processSignal(signalId);
                } catch (Exception e) {
                    log.warn("Signal worker: error processing signal {}: {}", signalId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Signal worker: poll error: {}", e.getMessage());
        }
    }

    /** Visible for IT: one poll cycle; returns candidate count (≤ claim batch). */
    public int pollOnceForTests() {
        List<Long> ids = signalRepository.listReadyDueIds(clock.nowUtcSeconds(), claimBatchSize);
        for (Long signalId : ids) {
            processingService.processSignal(signalId);
        }
        return ids.size();
    }

    public int claimBatchSize() {
        return claimBatchSize;
    }

    static int clampClaimBatch(int configured) {
        if (configured < 1) {
            return 1;
        }
        if (configured > 100) {
            return 100;
        }
        return configured;
    }
}
