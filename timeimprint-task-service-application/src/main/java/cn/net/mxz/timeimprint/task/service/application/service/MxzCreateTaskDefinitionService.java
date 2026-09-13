package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreatedDefinitionResult;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionCreatePort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzInitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MxzCreateTaskDefinitionService {

    private final ActorContextProvider actorContextProvider;
    private final ExtensionRegistry extensionRegistry;
    private final DefinitionCreatePort definitionCreatePort;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final TransitionPlanCommitter committer;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzCreateTaskDefinitionService(
            ActorContextProvider actorContextProvider,
            ExtensionRegistry extensionRegistry,
            DefinitionCreatePort definitionCreatePort,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            TransitionPlanCommitter committer,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.actorContextProvider = actorContextProvider;
        this.extensionRegistry = extensionRegistry;
        this.definitionCreatePort = definitionCreatePort;
        this.definitionRepository = definitionRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.committer = committer;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public MxzCreatedDefinitionResult create(
            String requestId,
            String scenarioKey,
            int scenarioSchemaVersion,
            String title,
            String description,
            String scenarioConfigJson,
            List<MxzCreateDefinitionCommand.ParticipantInput> participants,
            List<MxzCreateDefinitionCommand.TriggerBindingInput> triggerBindings) {
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions";
        byte[] hash = MxzSha256.digestUtf8(requestId + ":" + scenarioKey + ":" + title);
        return tx.execute(() -> {
            var existing = commandDedupRepository.find(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (existing.isPresent()) {
                assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    return replayCompleted(existing.get().responseJson());
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.find(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    assertSameRequestHash(again.get().requestHash(), hash);
                    if ("COMPLETED".equals(again.get().processStatus())) {
                        return replayCompleted(again.get().responseJson());
                    }
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }
            var ext = extensionRegistry.scenarioExtensions().require(new ScenarioExtensionKey(scenarioKey, 1));
            Map<String, Object> cfg = parse(scenarioConfigJson);
            ext.validateDefinitionConfig(new MxzDefinitionConfigValidationContext(
                    scenarioKey, scenarioSchemaVersion, new MxzJsonPayload(cfg)));
            validateParticipants(actor.principalId(), participants);
            Instant now = clock.nowUtcSeconds();
            var cmd = new MxzCreateDefinitionCommand(
                    actor.tenantKey(),
                    actor.principalType(),
                    actor.principalId(),
                    requestId,
                    scenarioKey,
                    scenarioSchemaVersion,
                    title,
                    description,
                    scenarioConfigJson,
                    participants,
                    triggerBindings,
                    now,
                    now.plus(7, ChronoUnit.DAYS),
                    requestId.replace("-", ""));
            var result = definitionCreatePort.createActive(cmd);

            // Invoke the scenario's initial definition hook; if Applied, commit the plan
            // (e.g. ScenarioDataMutations for scenario-specific tables). NoChange is the
            // common case for S01/S02 and results in a no-op.
            var initResult = ext.planInitialDefinition(new MxzInitialDefinitionContext(result.definition()));
            if (initResult instanceof HandlerResult.Applied applied
                    && !applied.plan().scenarioDataMutations().isEmpty()) {
                committer.commit(new MxzTransitionCommitRequest(
                        applied.plan(), result.definition().definitionId(), null, "SYSTEM", "initial_plan"));
            }

            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    op,
                    requestId,
                    "OK",
                    "DEFINITION",
                    String.valueOf(result.definition().definitionId()),
                    result.definition().revision(),
                    "{\"definitionId\":" + result.definition().definitionId() + "}");
            return result;
        });
    }

    private MxzCreatedDefinitionResult replayCompleted(String responseJson) {
        try {
            long definitionId = objectMapper.readTree(responseJson).path("definitionId").asLong();
            var def = definitionRepository
                    .findById(definitionId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            return new MxzCreatedDefinitionResult(
                    def, List.of(), List.of(), List.of(), 0, true, responseJson);
        } catch (MxzApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new MxzApplicationException("INTERNAL_ERROR", "idempotent replay failed");
        }
    }

    private void assertSameRequestHash(byte[] stored, byte[] incoming) {
        if (stored != null && !java.util.Arrays.equals(stored, incoming)) {
            throw new MxzApplicationException("IDEMPOTENCY_CONFLICT", "same requestId different payload");
        }
    }

    private void validateParticipants(
            String actorId, List<MxzCreateDefinitionCommand.ParticipantInput> participants) {
        boolean hasOwner = false;
        for (var p : participants) {
            if (!"USER".equals(p.principalType())) {
                throw new MxzApplicationException("INVALID_REQUEST", "only USER principal supported");
            }
            if (!actorId.equals(p.principalId())) {
                throw new MxzApplicationException("INVALID_REQUEST", "local actor must match participants");
            }
            if ("OWNER".equals(p.roleCode())) {
                hasOwner = true;
            }
        }
        if (!hasOwner) {
            throw new MxzApplicationException("INVALID_REQUEST", "OWNER required");
        }
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid scenarioConfig");
        }
    }
}
