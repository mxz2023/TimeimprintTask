package cn.net.mxz.timeimprint.task.common;

import java.time.Clock;
import java.time.Instant;

/**
 * 生产业务时钟：系统 UTC 截断到整秒。
 */
public final class MxzSystemUtcBusinessClock implements BusinessClock {

    private final Clock clock;

    public MxzSystemUtcBusinessClock() {
        this(Clock.systemUTC());
    }

    public MxzSystemUtcBusinessClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant nowUtcSeconds() {
        return BusinessClock.truncateToUtcSeconds(clock.instant());
    }
}
