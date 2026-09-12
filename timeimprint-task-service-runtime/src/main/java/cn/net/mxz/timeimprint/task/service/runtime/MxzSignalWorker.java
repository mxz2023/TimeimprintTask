package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker: claims READY/RETRY_WAIT signals due for processing
 * and dispatches each to MxzSignalProcessingService.
 *
 * Uses a simple poll-then-process pattern:
 * 1. List due signal IDs (no lock; lightweight).
 * 2. For each ID: MxzSignalProcessingService handles its own transaction with
 *    findByIdForUpdate and proper token management.
 */
@Component
public class MxzSignalWorker {

    private static final Logger log = LoggerFactory.getLogger(MxzSignalWorker.class);
    private static final int CLAIM_BATCH = 20;

    private final TaskSignalRepository signalRepository;
    private final MxzSignalProcessingService processingService;
    private final BusinessClock clock;

    public MxzSignalWorker(
            TaskSignalRepository signalRepository,
            MxzSignalProcessingService processingService,
            BusinessClock clock) {
        this.signalRepository = signalRepository;
        this.processingService = processingService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void pollAndProcess() {
        try {
            List<Long> ids = signalRepository.listReadyDueIds(clock.nowUtcSeconds(), CLAIM_BATCH);
            for (Long signalId : ids) {
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
}
