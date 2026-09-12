package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerPlannerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background planner: scans calendar trigger bindings whose 7-day window needs extending
 * and generates new WAITING instances + planned Signals.
 *
 * Delegates the actual storage work to TriggerPlannerPort (implemented in storage-mysql).
 */
@Component
public class MxzTriggerPlanner {

    private static final Logger log = LoggerFactory.getLogger(MxzTriggerPlanner.class);
    private static final int BINDING_BATCH = 10;
    private static final int OCC_PER_BINDING = 100;

    private final TriggerPlannerPort plannerPort;
    private final BusinessClock clock;

    public MxzTriggerPlanner(TriggerPlannerPort plannerPort, BusinessClock clock) {
        this.plannerPort = plannerPort;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void planNextWindows() {
        try {
            plannerPort.planDueBindings(clock.nowUtcSeconds(), BINDING_BATCH, OCC_PER_BINDING);
        } catch (Exception e) {
            log.warn("Trigger planner: error: {}", e.getMessage());
        }
    }
}
