package cn.net.mxz.timeimprint.task.service.application.definition.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.definition.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Definition control commands: pause / resume / retire.
 * Change reason: controlGeneration and window cancel/rebuild independent of update payload.
 */
@Component
public class DefinitionControlExecutor {

    private final ActorContextProvider actorContextProvider;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final DefinitionControlPort definitionControlPort;
    private final TransitionPlanCommitter committer;
    private final TransactionBoundary tx;
    private final BusinessClock clock;

    public DefinitionControlExecutor(
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

    public DefinitionCommandService.CommandResult execute(
            long definitionId,
            String commandKey,
            String requestId,
            long expectedRevision,
            int commandSchemaVersion,
            JsonNode payload) {
        if (commandSchemaVersion != 1) {
            throw new ApplicationException("INVALID_REQUEST", "commandSchemaVersion must be 1");
        }
        if (payload == null || !payload.isObject() || !payload.isEmpty()) {
            throw new ApplicationException("INVALID_REQUEST", "control command payload must be empty object");
        }
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions/" + definitionId + "/commands/" + commandKey;
        byte[] hash = Sha256.digestUtf8(requestId + ":" + commandKey);

        return tx.execute(() -> {
            var existing = commandDedupRepository.find(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (existing.isPresent()) {
                DefinitionCommandDedup.assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new DefinitionCommandService.CommandResult(commandKey, definitionId, def.revision(), false);
                }
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.find(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    DefinitionCommandDedup.assertSameRequestHash(again.get().requestHash(), hash);
                    if ("COMPLETED".equals(again.get().processStatus())) {
                        var def = definitionRepository
                                .findById(definitionId)
                                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                        return new DefinitionCommandService.CommandResult(commandKey, definitionId, def.revision(), false);
                    }
                }
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }

            var def = definitionRepository
                    .findByIdForUpdate(definitionId)
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.revision() != expectedRevision) {
                throw new ApplicationException("REVISION_CONFLICT", "definition revision mismatch");
            }

            ControlState current = def.controlState();
            ControlState target = resolveTarget(commandKey, current);

            if (target == current) {
                commandDedupRepository.complete(
                        actor.tenantKey(),
                        actor.principalId(),
                        op,
                        requestId,
                        "NO_CHANGE",
                        "DEFINITION",
                        String.valueOf(definitionId),
                        def.revision(),
                        "{}");
                return new DefinitionCommandService.CommandResult(commandKey, definitionId, def.revision(), false);
            }

            if ("pause".equals(commandKey) || "retire".equals(commandKey)) {
                definitionControlPort.cancelWindowAndSignals(definitionId, clock.nowUtcSeconds());
            }

            var controlTransition = new DefinitionControlTransition(current, target);
            var planTarget = new TransitionTarget(TransitionResourceType.DEFINITION, definitionId, def.revision());
            var plan = new TransitionPlan(
                    planTarget,
                    controlTransition,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    commandKey + ":" + definitionId);

            var result = committer.commit(new TransitionCommitRequest(
                    plan, definitionId, null, "COMMAND", commandKey));

            if ("resume".equals(commandKey)) {
                Instant now = clock.nowUtcSeconds();
                definitionControlPort.rebuildFutureWindow(definitionId, now);
            }

            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    op,
                    requestId,
                    "APPLIED",
                    "DEFINITION",
                    String.valueOf(definitionId),
                    result.toRevision(),
                    "{\"definitionId\":" + definitionId + "}");
            return new DefinitionCommandService.CommandResult(commandKey, definitionId, result.toRevision(), true);
        });
    }


    private ControlState resolveTarget(String commandKey, ControlState current) {
        return switch (commandKey) {
            case "pause" -> {
                if (current == ControlState.ACTIVE) yield ControlState.PAUSED;
                if (current == ControlState.PAUSED) yield ControlState.PAUSED;
                throw new ApplicationException("INVALID_STATE", "pause requires ACTIVE, got " + current);
            }
            case "resume" -> {
                if (current == ControlState.PAUSED) yield ControlState.ACTIVE;
                if (current == ControlState.ACTIVE) yield ControlState.ACTIVE;
                throw new ApplicationException("INVALID_STATE", "resume requires PAUSED, got " + current);
            }
            case "retire" -> {
                if (current == ControlState.RETIRED) yield ControlState.RETIRED;
                yield ControlState.RETIRED;
            }
            default -> throw new ApplicationException(
                    "COMMAND_NOT_SUPPORTED",
                    "定义命令 commandKey=" + commandKey + " 不受支持。请使用 update/pause/resume/retire");
        };
    }
}
