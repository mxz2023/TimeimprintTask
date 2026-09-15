package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;

/** 专有数据物化输入（同一迁移事务内调用）。 */
public record ScenarioDataMaterializationContext(
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        long transitionId,
        ScenarioDataMutation mutation) {}
