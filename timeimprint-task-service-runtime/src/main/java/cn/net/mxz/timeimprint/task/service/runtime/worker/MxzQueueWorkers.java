package cn.net.mxz.timeimprint.task.service.runtime.worker;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.TaskSignalRepository;
import cn.net.mxz.timeimprint.task.service.application.service.MxzSignalProcessingService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 遗留双队列轮询（曾与 MxzSignalWorker / MxzActionWorker 重复）。
 * 保留实现供对照；默认不注册为 Spring Bean，避免双调度。
 * 若需启用：恢复 {@code @Component} 并关闭独立 Worker。
 */
public class MxzQueueWorkers {

    private static final Logger log = LoggerFactory.getLogger(MxzQueueWorkers.class);

    private final TaskSignalRepository signalRepository;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final MxzSignalProcessingService signalProcessingService;
    private final BusinessClock clock;
    private final boolean workerEnabled;
    private final int claimBatchSize;

    public MxzQueueWorkers(
            TaskSignalRepository signalRepository,
            ActionJobExecutionPort actionJobExecutionPort,
            MxzSignalProcessingService signalProcessingService,
            BusinessClock clock,
            @Value("${WORKER_ENABLED:true}") boolean workerEnabled,
            @Value("${CLAIM_BATCH_SIZE:50}") int claimBatchSize) {
        this.signalRepository = signalRepository;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.signalProcessingService = signalProcessingService;
        this.clock = clock;
        this.workerEnabled = workerEnabled;
        this.claimBatchSize = claimBatchSize;
    }

    @Scheduled(fixedDelayString = "${SIGNAL_SCAN_MS:1000}")
    public void pollSignals() {
        if (!workerEnabled) {
            return;
        }
        Instant now = clock.nowUtcSeconds();
        // IDs only — claimReadySignals historically returned signal_id alone and NPE'd in toSignal.
        List<Long> candidates = signalRepository.listReadyDueIds(now, claimBatchSize);
        for (Long signalId : candidates) {
            try {
                signalProcessingService.processSignal(signalId);
            } catch (Exception e) {
                log.warn("signal process failed id={}: {}", signalId, e.toString());
            }
        }
    }

    @Scheduled(fixedDelayString = "${ACTION_SCAN_MS:1000}")
    public void pollActions() {
        if (!workerEnabled) {
            return;
        }
        Instant now = clock.nowUtcSeconds();
        List<Long> candidates = actionJobExecutionPort.listReadyDueIds(now, claimBatchSize);
        for (long actionJobId : candidates) {
            try {
                signalProcessingService.executeClaimedAction(actionJobId, null);
            } catch (Exception e) {
                log.warn("action process failed id={}: {}", actionJobId, e.toString());
            }
        }
    }
}
