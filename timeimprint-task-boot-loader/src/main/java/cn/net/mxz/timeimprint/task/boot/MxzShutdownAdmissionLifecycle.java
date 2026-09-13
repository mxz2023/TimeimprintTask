package cn.net.mxz.timeimprint.task.boot;

import cn.net.mxz.timeimprint.task.service.application.runtime.MxzRuntimeAdmission;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * A39 / 03 §5：停机最早阶段先 REFUSING_TRAFFIC，并关闭新写与队列领取。
 */
@Component
public class MxzShutdownAdmissionLifecycle implements SmartLifecycle {

    private final MxzRuntimeAdmission admission;
    private final ApplicationContext applicationContext;
    private volatile boolean running;

    public MxzShutdownAdmissionLifecycle(MxzRuntimeAdmission admission, ApplicationContext applicationContext) {
        this.admission = admission;
        this.applicationContext = applicationContext;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        beginRefuse();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        beginRefuse();
        running = false;
        callback.run();
    }

    private void beginRefuse() {
        admission.beginShutdown();
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Run before web server graceful drain so new writes are rejected first. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
