package cn.net.mxz.timeimprint.task.service.kernel.domain.identity;

/**
 * 任务定义稳定标识。
 */
public record TaskDefinitionId(long value) {
    public TaskDefinitionId {
        if (value <= 0) {
            throw new IllegalArgumentException("definitionId must be positive");
        }
    }
}
