package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;

/** Signal Worker 锁内快照与载荷。 */
public record SignalProcessContext(
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        long signalId,
        String providerKey,
        String signalKey,
        int schemaVersion,
        ScenarioMutationPayload payload) {}
