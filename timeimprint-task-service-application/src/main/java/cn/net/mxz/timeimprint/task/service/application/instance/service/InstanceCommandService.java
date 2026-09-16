package cn.net.mxz.timeimprint.task.service.application.instance.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.InstanceCommandPort;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitResult;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.extension.command.spi.CommandScope;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionJobView;
import cn.net.mxz.timeimprint.task.service.extension.command.context.CommandExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.command.registry.TaskCommandHandlerKey;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
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
public class InstanceCommandService {

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

    public InstanceCommandService(
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
            long expectedRevision,
            String payloadJson) {

        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-instances/" + instanceId + "/commands/" + commandKey;
        byte[] hash = Sha256.digestUtf8(requestId + ":" + commandKey + ":" + payloadJson);

        return tx.execute(() -> {
            var existing = commandDedupRepository.find(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (existing.isPresent()) {
                assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    var inst = instanceRepository.findById(instanceId)
                            .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
                    return new InstanceCommandResult(instanceId, inst.definitionId(), inst.revision(), false,
                            inst.scenarioState(), Map.of());
                }
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.find(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    assertSameRequestHash(again.get().requestHash(), hash);
                    if ("COMPLETED".equals(again.get().processStatus())) {
                        var inst = instanceRepository.findById(instanceId)
                                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
                        return new InstanceCommandResult(instanceId, inst.definitionId(), inst.revision(), false,
                                inst.scenarioState(), Map.of());
                    }
                }
                throw new ApplicationException("RETRY_LATER", "dedup in progress");
            }

            var instSnap = instanceRepository.findByIdForUpdate(instanceId)
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
            var defSnap = definitionRepository.findByIdForUpdate(instSnap.definitionId())
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));

            if (instSnap.revision() != expectedRevision) {
                commandDedupRepository.complete(
                        actor.tenantKey(),
                        actor.principalId(),
                        op,
                        requestId,
                        "REVISION_CONFLICT",
                        "INSTANCE",
                        String.valueOf(instanceId),
                        instSnap.revision(),
                        "{\"error\":\"REVISION_CONFLICT\"}");
                throw new ApplicationException(
                        "REVISION_CONFLICT",
                        "expectedRevision=" + expectedRevision + " current=" + instSnap.revision());
            }

            var handlerKey = new TaskCommandHandlerKey(
                    defSnap.scenarioKey(), CommandScope.INSTANCE, commandKey, commandSchemaVersion);
            var handlerOpt = extensionRegistry.commandHandlers().find(handlerKey);
            if (handlerOpt.isEmpty()) {
                throw new ApplicationException(
                        "COMMAND_NOT_SUPPORTED",
                        "场景 "
                                + defSnap.scenarioKey()
                                + " 未声明命令 commandKey="
                                + commandKey
                                + "。请先查询该资源的 allowedCommands，改用已声明命令后再试");
            }
            var handler = handlerOpt.get();

            Instant now = clock.nowUtcSeconds();
            Map<String, Object> payloadFields = parseJson(payloadJson);
            List<ActionJobView> actionViews = actionJobExecutionPort.listByInstance(instanceId).stream()
                    .map(a -> new ActionJobView(
                            a.actionJobId(),
                            a.actionKey(),
                            a.status(),
                            a.availableAt(),
                            a.expiresAt(),
                            a.payloadJson()))
                    .toList();
            var ctx = new CommandExecutionContext(
                    CommandScope.INSTANCE,
                    commandKey,
                    commandSchemaVersion,
                    requestId,
                    defSnap,
                    instSnap,
                    new JsonPayload(payloadFields),
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
                    throw new ApplicationException(rejected.reasonCode(), rejected.safeMessage());
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
                    TransitionCommitResult commit = committer.commit(
                            new TransitionCommitRequest(
                                    applied.plan(), instSnap.definitionId(), instanceId, "COMMAND", commandKey));
                    newRevision = commit.toRevision();
                    changed = true;
                    var planTransition = applied.plan().instanceStateTransition();
                    if (planTransition != null
                            && planTransition.toLifecycleCategory() == LifecycleCategory.TERMINAL) {
                        if (!applied.plan().actionJobIntents().isEmpty()) {
                            instanceCommandPort.cancelRemainingActionsExceptTransition(
                                    instanceId, commit.transitionId(), now);
                        } else {
                            instanceCommandPort.cancelRemainingActions(instanceId, now);
                        }
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
                    .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
            return new InstanceCommandResult(instanceId, instSnap.definitionId(), updated.revision(), changed,
                    updated.scenarioState(), scenarioResult);
        });
    }

    private Map<String, Object> buildSnoozeScenarioResult(long instanceId) {
        var inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
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
            throw new ApplicationException("INVALID_REQUEST", "invalid payload json");
        }
    }

    private void assertSameRequestHash(byte[] stored, byte[] incoming) {
        if (stored != null && !java.util.Arrays.equals(stored, incoming)) {
            throw new ApplicationException("IDEMPOTENCY_CONFLICT", "same requestId different payload");
        }
    }
}
