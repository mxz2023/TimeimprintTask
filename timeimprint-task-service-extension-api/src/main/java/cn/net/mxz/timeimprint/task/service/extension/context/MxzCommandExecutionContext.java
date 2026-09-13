package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import java.time.Instant;
import java.util.List;

/** 同步命令锁内快照与已解码命令载荷。 */
public record MxzCommandExecutionContext(
        CommandScope scope,
        String commandKey,
        int commandSchemaVersion,
        String requestId,
        MxzTaskDefinitionSnapshot definitionSnapshot,
        MxzTaskInstanceSnapshot instanceSnapshot,
        ScenarioMutationPayload commandPayload,
        Instant nowUtc,
        List<MxzActionJobView> actionJobs) {

    public MxzCommandExecutionContext {
        actionJobs = actionJobs == null ? List.of() : List.copyOf(actionJobs);
        if (nowUtc == null) {
            throw new IllegalArgumentException("nowUtc required");
        }
    }
}
