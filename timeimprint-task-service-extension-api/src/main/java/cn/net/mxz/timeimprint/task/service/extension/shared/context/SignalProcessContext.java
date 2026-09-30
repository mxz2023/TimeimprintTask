package cn.net.mxz.timeimprint.task.service.extension.shared.context;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;

/** Signal Worker 锁内快照与载荷。 */
public record SignalProcessContext(
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        long signalId,
        String providerKey,
        String signalKey,
        int schemaVersion,
        ScenarioMutationPayload payload) {}
