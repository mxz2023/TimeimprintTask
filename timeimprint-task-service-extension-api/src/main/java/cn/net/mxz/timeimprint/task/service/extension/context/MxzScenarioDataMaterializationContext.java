package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;

/** 专有数据物化输入（同一迁移事务内调用）。 */
public record MxzScenarioDataMaterializationContext(
        MxzTaskDefinitionSnapshot definitionSnapshot,
        MxzTaskInstanceSnapshot instanceSnapshot,
        long transitionId,
        ScenarioDataMutation mutation) {}
