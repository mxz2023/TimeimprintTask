package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * E06: Definition-level commands: pause / resume / retire.
 * "update" is partially supported (simplified: updates title/description only).
 * All control commands cancel WAITING instances and future Signals atomically.
 */
@Service
public class MxzDefinitionCommandService {

    private final ActorContextProvider actorContextProvider;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final DefinitionControlPort definitionControlPort;
    private final TransitionPlanCommitter committer;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public MxzDefinitionCommandService(
            ActorContextProvider actorContextProvider,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            DefinitionControlPort definitionControlPort,
            TransitionPlanCommitter committer,
            TransactionBoundary tx,
            BusinessClock clock) {
        this.actorContextProvider = actorContextProvider;
        this.definitionRepository = definitionRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.definitionControlPort = definitionControlPort;
        this.committer = committer;
        this.tx = tx;
        this.clock = clock;
    }

    public record CommandResult(String commandKey, long definitionId, long revision) {}

    /**
     * pause / resume / retire a definition.
     */
    public CommandResult execute(long definitionId, String commandKey, String requestId) {
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions/" + definitionId + "/commands/" + commandKey;
        byte[] hash = MxzSha256.digestUtf8(requestId + ":" + commandKey);

        return tx.execute(() -> {
            // Idempotency
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                throw new MxzApplicationException("IDEMPOTENCY_REPLAY", completed.get());
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    throw new MxzApplicationException("IDEMPOTENCY_REPLAY", again.get());
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }

            // Lock definition
            var def = definitionRepository.findByIdForUpdate(definitionId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));

            ControlState current = def.controlState();
            ControlState target = resolveTarget(commandKey, current);

            if (target == current) {
                commandDedupRepository.complete(
                        actor.tenantKey(), actor.principalId(), op, requestId,
                        "NO_CHANGE", "DEFINITION", String.valueOf(definitionId),
                        def.revision(), "{}");
                return new CommandResult(commandKey, definitionId, def.revision());
            }

            // For pause/retire: cancel WAITING instances + future signals atomically
            if ("pause".equals(commandKey) || "retire".equals(commandKey)) {
                definitionControlPort.cancelWindowAndSignals(definitionId, clock.nowUtcSeconds());
            }

            // Build transition plan with definition control transition
            var controlTransition = new DefinitionControlTransition(current, target);
            var planTarget = new TransitionTarget(
                    TransitionResourceType.DEFINITION, definitionId, def.revision());
            var plan = new TransitionPlan(
                    planTarget,
                    controlTransition,
                    null,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    commandKey + ":" + definitionId);

            var result = committer.commit(new MxzTransitionCommitRequest(
                    plan, definitionId, null, "COMMAND", commandKey));

            commandDedupRepository.complete(
                    actor.tenantKey(), actor.principalId(), op, requestId,
                    "APPLIED", "DEFINITION", String.valueOf(definitionId),
                    result.toRevision(), "{\"definitionId\":" + definitionId + "}");
            return new CommandResult(commandKey, definitionId, result.toRevision());
        });
    }

    private ControlState resolveTarget(String commandKey, ControlState current) {
        return switch (commandKey) {
            case "pause" -> {
                if (current == ControlState.ACTIVE) yield ControlState.PAUSED;
                if (current == ControlState.PAUSED) yield ControlState.PAUSED; // idempotent
                throw new MxzApplicationException("INVALID_STATE",
                        "pause requires ACTIVE, got " + current);
            }
            case "resume" -> {
                if (current == ControlState.PAUSED) yield ControlState.ACTIVE;
                if (current == ControlState.ACTIVE) yield ControlState.ACTIVE; // idempotent
                throw new MxzApplicationException("INVALID_STATE",
                        "resume requires PAUSED, got " + current);
            }
            case "retire" -> {
                if (current == ControlState.RETIRED) yield ControlState.RETIRED; // idempotent
                yield ControlState.RETIRED;
            }
            default -> throw new MxzApplicationException("COMMAND_NOT_SUPPORTED",
                    "definition command '" + commandKey + "' not supported");
        };
    }
}
