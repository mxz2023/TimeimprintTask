package cn.net.mxz.timeimprint.task.service.runtime.trigger.worker;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerPlannerPort;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
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
public class TriggerPlanner {

    private static final Logger log = LoggerFactory.getLogger(TriggerPlanner.class);
    private static final int BINDING_BATCH = 10;
    private static final int OCC_PER_BINDING = 100;

    private final TriggerPlannerPort plannerPort;
    private final BusinessClock clock;
    private final RuntimeAdmission admission;

    public TriggerPlanner(
            TriggerPlannerPort plannerPort, BusinessClock clock, RuntimeAdmission admission) {
        this.plannerPort = plannerPort;
        this.clock = clock;
        this.admission = admission;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void planNextWindows() {
        if (!admission.acceptingClaims()) {
            return;
        }
        try {
            plannerPort.planDueBindings(clock.nowUtcSeconds(), BINDING_BATCH, OCC_PER_BINDING);
        } catch (Exception e) {
            log.warn("Trigger planner: error: {}", e.getMessage());
        }
    }
}
