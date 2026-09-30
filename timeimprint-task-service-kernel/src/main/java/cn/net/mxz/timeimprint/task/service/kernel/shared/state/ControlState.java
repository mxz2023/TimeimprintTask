package cn.net.mxz.timeimprint.task.service.kernel.shared.state;

/**
 * 定义控制状态：是否接受新输入（与实例生命周期独立）。
 */
public enum ControlState {
    ACTIVE,
    PAUSED,
    RETIRED
}
