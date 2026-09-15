package cn.net.mxz.timeimprint.task.service.extension.context;

import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;
import java.time.Instant;
import java.util.List;

/** 同步命令锁内快照与已解码命令载荷。 */
public record CommandExecutionContext(
        CommandScope scope,
        String commandKey,
        int commandSchemaVersion,
        String requestId,
        TaskDefinitionSnapshot definitionSnapshot,
        TaskInstanceSnapshot instanceSnapshot,
        ScenarioMutationPayload commandPayload,
        Instant nowUtc,
        List<ActionJobView> actionJobs) {

    public CommandExecutionContext {
        actionJobs = actionJobs == null ? List.of() : List.copyOf(actionJobs);
        if (nowUtc == null) {
            throw new IllegalArgumentException("nowUtc required");
        }
    }
}
