package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitResult;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzCommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * E09 実例级命令管道：command-dedup → 父级锁（definition）→ 子级锁（instance）
 * → TaskCommandHandler → TransitionPlanCommitter → dedup complete。
 */
@Service
public class MxzInstanceCommandService {

    private final ActorContextProvider actorContextProvider;
    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final ExtensionRegistry extensionRegistry;
    private final TransitionPlanCommitter committer;
    private final InstanceCommandPort instanceCommandPort;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzInstanceCommandService(
            ActorContextProvider actorContextProvider,
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            CommandDedupRepository commandDedupRepository,
            ExtensionRegistry extensionRegistry,
            TransitionPlanCommitter committer,
            InstanceCommandPort instanceCommandPort,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.actorContextProvider = actorContextProvider;
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.extensionRegistry = extensionRegistry;
        this.committer = committer;
        this.instanceCommandPort = instanceCommandPort;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public record InstanceCommandResult(
            long instanceId,
            long definitionId,
            long newRevision,
            boolean changed,
            String scenarioState,
            Map<String, Object> scenarioResult) {}

    public InstanceCommandResult execute(
            long instanceId,
            String commandKey,
            int commandSchemaVersion,
            String requestId,
            String payloadJson) {

        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-instances/" + instanceId + "/commands/" + commandKey;
        byte[] hash = MxzSha256.digestUtf8(requestId + ":" + commandKey + ":" + payloadJson);

        return tx.execute(() -> {
            // 1. Idempotency check
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                // reload from DB and return current state
                var inst = instanceRepository.findById(instanceId)
                        .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
                return new InstanceCommandResult(instanceId, inst.definitionId(), inst.revision(), false,
                        inst.scenarioState(), Map.of());
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    var inst = instanceRepository.findById(instanceId)
                            .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
                    return new InstanceCommandResult(instanceId, inst.definitionId(), inst.revision(), false,
                            inst.scenarioState(), Map.of());
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }

            // 2. Lock instance first to get definitionId, then lock definition (parent-first)
            var instSnap = instanceRepository.findByIdForUpdate(instanceId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
            var defSnap = definitionRepository.findByIdForUpdate(instSnap.definitionId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));

            // 3. Find the registered TaskCommandHandler
            var handlerKey = new TaskCommandHandlerKey(defSnap.scenarioKey(), CommandScope.INSTANCE, commandKey, commandSchemaVersion);
            var handlerOpt = extensionRegistry.commandHandlers().find(handlerKey);
            if (handlerOpt.isEmpty()) {
                throw new MxzApplicationException("EXTENSION_NOT_FOUND",
                        "no handler for " + defSnap.scenarioKey() + "/" + commandKey + "/v" + commandSchemaVersion);
            }
            var handler = handlerOpt.get();

            // 4. Parse payload and build context
            Map<String, Object> payloadFields = parseJson(payloadJson);
            var ctx = new MxzCommandExecutionContext(
                    CommandScope.INSTANCE,
                    commandKey,
                    commandSchemaVersion,
                    requestId,
                    defSnap,
                    instSnap,
                    new MxzJsonPayload(payloadFields));

            // 5. Execute handler
            HandlerResult result = handler.handle(ctx);

            Instant now = clock.nowUtcSeconds();
            boolean changed = false;
            long newRevision = instSnap.revision();

            switch (result) {
                case HandlerResult.Rejected rejected -> {
                    commandDedupRepository.complete(actor.tenantKey(), actor.principalId(), op, requestId,
                            rejected.reasonCode(), "INSTANCE", String.valueOf(instanceId), newRevision,
                            "{\"error\":\"" + rejected.reasonCode() + "\"}");
                    throw new MxzApplicationException(rejected.reasonCode(), rejected.safeMessage());
                }
                case HandlerResult.NoChange nc -> {
                    commandDedupRepository.complete(actor.tenantKey(), actor.principalId(), op, requestId,
                            "NO_CHANGE", "INSTANCE", String.valueOf(instanceId), newRevision,
                            "{\"changed\":false}");
                }
                case HandlerResult.Applied applied -> {
                    MxzTransitionCommitResult commit = committer.commit(
                            new MxzTransitionCommitRequest(
                                    applied.plan(), instSnap.definitionId(), instanceId, "COMMAND", commandKey));
                    newRevision = commit.toRevision();
                    changed = true;
                    // Cancel remaining pending actions if the instance reached TERMINAL lifecycle
                    var planTransition = applied.plan().instanceStateTransition();
                    if (planTransition != null
                            && planTransition.toLifecycleCategory() == cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory.TERMINAL) {
                        instanceCommandPort.cancelRemainingActions(instanceId, now);
                    }
                    commandDedupRepository.complete(actor.tenantKey(), actor.principalId(), op, requestId,
                            "OK", "INSTANCE", String.valueOf(instanceId), newRevision,
                            "{\"changed\":true,\"revision\":" + newRevision + "}");
                }
            }

            // Reload instance state after possible commit
            var updated = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
            return new InstanceCommandResult(instanceId, instSnap.definitionId(), updated.revision(), changed,
                    updated.scenarioState(), Map.of());
        });
    }

    private Map<String, Object> parseJson(String json) {
        try {
            if (json == null || json.isBlank() || "null".equals(json.trim())) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid payload json");
        }
    }
}
