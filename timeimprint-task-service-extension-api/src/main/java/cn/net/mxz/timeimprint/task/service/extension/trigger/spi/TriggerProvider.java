package cn.net.mxz.timeimprint.task.service.extension.trigger.spi;

import cn.net.mxz.timeimprint.task.service.extension.trigger.context.TriggerEvaluationContext;
import cn.net.mxz.timeimprint.task.service.extension.trigger.registry.TriggerProviderKey;
import cn.net.mxz.timeimprint.task.service.kernel.shared.model.PlannedSignalIntent;
import java.util.List;
import java.util.Set;

/** 将时间/事件/条件转换为去重 Signal 意图，不改变任务业务状态。 */
public interface TriggerProvider {

    TriggerProviderKey registrationKey();

    /** 实现可读的配置与 payload schemaVersion 集合。 */
    Set<Integer> supportedSchemaVersions();

    List<PlannedSignalIntent> evaluate(TriggerEvaluationContext context);
}
