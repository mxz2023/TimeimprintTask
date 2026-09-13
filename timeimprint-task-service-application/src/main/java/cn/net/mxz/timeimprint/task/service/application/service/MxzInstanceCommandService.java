package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitResult;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.extension.command.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzActionJobView;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzCommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * E09 实例级命令管道：command-dedup → 父级锁（definition）→ 子级锁（instance）
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
    private final ActionJobExecutionPort actionJobExecutionPort;
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
            ActionJobExecutionPort actionJobExecutionPort,
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
        this.actionJobExecutionPort = actionJobExecutionPort;
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
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
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

            var instSnap = instanceRepository.findByIdForUpdate(instanceId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
            var defSnap = definitionRepository.findByIdForUpdate(instSnap.definitionId())
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));

            var handlerKey = new TaskCommandHandlerKey(
                    defSnap.scenarioKey(), CommandScope.INSTANCE, commandKey, commandSchemaVersion);
            var handlerOpt = extensionRegistry.commandHandlers().find(handlerKey);
            if (handlerOpt.isEmpty()) {
                throw new MxzApplicationException("EXTENSION_NOT_FOUND",
                        "no handler for " + defSnap.scenarioKey() + "/" + commandKey + "/v" + commandSchemaVersion);
            }
            var handler = handlerOpt.get();

            Instant now = clock.nowUtcSeconds();
            Map<String, Object> payloadFields = parseJson(payloadJson);
            List<MxzActionJobView> actionViews = actionJobExecutionPort.listByInstance(instanceId).stream()
                    .map(a -> new MxzActionJobView(
                            a.actionJobId(),
                            a.actionKey(),
                            a.status(),
                            a.availableAt(),
                            a.expiresAt(),
                            a.payloadJson()))
                    .toList();
            var ctx = new MxzCommandExecutionContext(
                    CommandScope.INSTANCE,
                    commandKey,
                    commandSchemaVersion,
                    requestId,
                    defSnap,
                    instSnap,
                    new MxzJsonPayload(payloadFields),
                    now,
                    actionViews);

            HandlerResult result = handler.handle(ctx);

            boolean changed = false;
            long newRevision = instSnap.revision();
            Map<String, Object> scenarioResult = Map.of();

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
                    // snooze: cancel movable READY/RETRY_WAIT before inserting the new generation
                    if ("snooze".equals(commandKey)) {
                        instanceCommandPort.cancelRemainingActions(instanceId, now);
                    }
                    MxzTransitionCommitResult commit = committer.commit(
                            new MxzTransitionCommitRequest(
                                    applied.plan(), instSnap.definitionId(), instanceId, "COMMAND", commandKey));
                    newRevision = commit.toRevision();
                    changed = true;
                    var planTransition = applied.plan().instanceStateTransition();
                    if (planTransition != null
                            && planTransition.toLifecycleCategory() == LifecycleCategory.TERMINAL) {
                        instanceCommandPort.cancelRemainingActions(instanceId, now);
                    }
                    if ("snooze".equals(commandKey)) {
                        scenarioResult = buildSnoozeScenarioResult(instanceId);
                    }
                    commandDedupRepository.complete(actor.tenantKey(), actor.principalId(), op, requestId,
                            "OK", "INSTANCE", String.valueOf(instanceId), newRevision,
                            "{\"changed\":true,\"revision\":" + newRevision + "}");
                }
            }

            var updated = instanceRepository.findById(instanceId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
            return new InstanceCommandResult(instanceId, instSnap.definitionId(), updated.revision(), changed,
                    updated.scenarioState(), scenarioResult);
        });
    }

    private Map<String, Object> buildSnoozeScenarioResult(long instanceId) {
        var inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
        Map<String, Object> snap = parseJson(inst.scenarioSnapshotJson());
        List<String> remaining = actionJobExecutionPort.listByInstance(instanceId).stream()
                .filter(a -> "READY".equals(a.status()) || "RETRY_WAIT".equals(a.status()))
                .sorted((a, b) -> a.availableAt().compareTo(b.availableAt()))
                .map(a -> a.availableAt().toString())
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snoozeCount", snap.getOrDefault("snoozeCount", 0));
        result.put("actionGeneration", snap.getOrDefault("actionGeneration", 1));
        result.put("remainingReminderAts", remaining);
        return result;
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
