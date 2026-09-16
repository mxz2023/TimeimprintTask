package cn.net.mxz.timeimprint.task.service.kernel.instance.identity;

/**
 * 任务实例稳定标识。
 */
public record TaskInstanceId(long value) {
    public TaskInstanceId {
        if (value <= 0) {
            throw new IllegalArgumentException("instanceId must be positive");
        }
    }
}
