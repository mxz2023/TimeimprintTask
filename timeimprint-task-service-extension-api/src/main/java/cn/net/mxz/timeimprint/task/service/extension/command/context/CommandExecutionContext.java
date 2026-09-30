package cn.net.mxz.timeimprint.task.service.extension.command.context;

import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.time.Instant;
import java.util.List;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionJobView;

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
