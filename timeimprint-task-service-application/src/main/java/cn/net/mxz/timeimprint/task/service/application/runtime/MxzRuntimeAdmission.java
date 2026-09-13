package cn.net.mxz.timeimprint.task.service.application.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * A39 / 03 §5 / 04：正常停机后拒绝新写并停止队列领取；已进入事务的工作仍可闭合。
 */
@Component
public class MxzRuntimeAdmission {

    private final AtomicBoolean acceptingWrites = new AtomicBoolean(true);
    private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

    public boolean acceptingWrites() {
        return acceptingWrites.get();
    }

    public boolean acceptingClaims() {
        return acceptingClaims.get();
    }

    /** Idempotent: first call flips both gates off. */
    public void beginShutdown() {
        acceptingWrites.set(false);
        acceptingClaims.set(false);
    }

    /** Test / recovery hook only. */
    public void resetForTests() {
        acceptingWrites.set(true);
        acceptingClaims.set(true);
    }
}
