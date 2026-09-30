package cn.net.mxz.timeimprint.task.common.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 业务时钟：UTC 整秒截断。
 */
public interface BusinessClock {

    Instant nowUtcSeconds();

    static Instant truncateToUtcSeconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
