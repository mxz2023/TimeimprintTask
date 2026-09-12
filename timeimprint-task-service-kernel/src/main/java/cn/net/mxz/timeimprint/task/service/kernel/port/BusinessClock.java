package cn.net.mxz.timeimprint.task.service.kernel.port;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 平台业务时钟端口；内核与纯领域逻辑通过此接口获取时间，不依赖系统默认时区策略的具体实现。
 */
public interface BusinessClock {

    /** 当前业务时刻（UTC，整秒精度由实现保证）。 */
    Instant nowInstant();

    /** 与持久化 DATETIME(0) 对齐的业务本地视图（实现定义时区映射）。 */
    LocalDateTime nowDateTime();
}
