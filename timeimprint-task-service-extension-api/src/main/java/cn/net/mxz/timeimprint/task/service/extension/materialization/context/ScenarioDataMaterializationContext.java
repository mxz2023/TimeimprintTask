package cn.net.mxz.timeimprint.task.service.extension.materialization.context;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;

/** 专有数据物化输入（同一迁移事务内调用）。 */
public record ScenarioDataMaterializationContext(
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        long transitionId,
        ScenarioDataMutation mutation) {}
