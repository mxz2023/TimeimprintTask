package cn.net.mxz.timeimprint.task.service.extension.spi;

import cn.net.mxz.timeimprint.task.service.extension.context.TriggerEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.PlannedSignalIntent;
import java.util.List;
import java.util.Set;

/** 将时间/事件/条件转换为去重 Signal 意图，不改变任务业务状态。 */
public interface TriggerProvider {

    TriggerProviderKey registrationKey();

    /** 实现可读的配置与 payload schemaVersion 集合。 */
    Set<Integer> supportedSchemaVersions();

    List<PlannedSignalIntent> evaluate(TriggerEvaluationContext context);
}
