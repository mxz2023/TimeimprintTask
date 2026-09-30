package cn.net.mxz.timeimprint.task.service.kernel.shared.state;

/**
 * 实例生命周期类别，供通用查询、并发屏障和保留策略使用。
 */
public enum LifecycleCategory {
    WAITING,
    ACTIVE,
    TERMINAL
}
