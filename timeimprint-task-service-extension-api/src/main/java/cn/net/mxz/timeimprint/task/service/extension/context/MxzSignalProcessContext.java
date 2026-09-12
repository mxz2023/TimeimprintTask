package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;

/** Signal Worker 锁内快照与载荷。 */
public record MxzSignalProcessContext(
        MxzTaskDefinitionSnapshot definitionSnapshot,
        MxzTaskInstanceSnapshot instanceSnapshot,
        long signalId,
        String providerKey,
        String signalKey,
        int schemaVersion,
        ScenarioMutationPayload payload) {}
