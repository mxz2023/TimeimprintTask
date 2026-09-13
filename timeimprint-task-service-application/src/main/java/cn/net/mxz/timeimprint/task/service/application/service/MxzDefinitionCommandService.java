package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionUpdatePort;
import cn.net.mxz.timeimprint.task.service.application.port.MxzTransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * E06: Definition-level commands: update / pause / resume / retire.
 */
@Service
public class MxzDefinitionCommandService {

    private static final Set<String> UPDATE_FIELDS = Set.of(
            "scenarioSchemaVersion",
            "title",
            "description",
            "participants",
            "triggerBindings",
            "scenarioConfig");

    private final ActorContextProvider actorContextProvider;
    private final TaskDefinitionRepository definitionRepository;
    private final CommandDedupRepository commandDedupRepository;
    private final DefinitionControlPort definitionControlPort;
    private final DefinitionUpdatePort definitionUpdatePort;
    private final ExtensionRegistry extensionRegistry;
    private final TransitionPlanCommitter committer;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;

    public MxzDefinitionCommandService(
            ActorContextProvider actorContextProvider,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            DefinitionControlPort definitionControlPort,
            DefinitionUpdatePort definitionUpdatePort,
            ExtensionRegistry extensionRegistry,
            TransitionPlanCommitter committer,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper) {
        this.actorContextProvider = actorContextProvider;
        this.definitionRepository = definitionRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.definitionControlPort = definitionControlPort;
        this.definitionUpdatePort = definitionUpdatePort;
        this.extensionRegistry = extensionRegistry;
        this.committer = committer;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public record CommandResult(String commandKey, long definitionId, long revision, boolean changed) {}

    public CommandResult execute(
            long definitionId,
            String commandKey,
            String requestId,
            long expectedRevision,
            int commandSchemaVersion,
            JsonNode payload) {
        if ("update".equals(commandKey)) {
            return executeUpdate(definitionId, requestId, expectedRevision, commandSchemaVersion, payload);
        }
        return executeControl(definitionId, commandKey, requestId, expectedRevision, commandSchemaVersion, payload);
    }

    private CommandResult executeUpdate(
            long definitionId,
            String requestId,
            long expectedRevision,
            int commandSchemaVersion,
            JsonNode payload) {
        if (commandSchemaVersion != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "commandSchemaVersion must be 1");
        }
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions/" + definitionId + "/commands/update";
        byte[] hash = MxzSha256.digestUtf8(requestId + ":update:" + payload);

        return tx.execute(() -> {
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                var def = definitionRepository
                        .findById(definitionId)
                        .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
                return new CommandResult("update", definitionId, def.revision(), false);
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new CommandResult("update", definitionId, def.revision(), false);
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }

            var def = definitionRepository
                    .findByIdForUpdate(definitionId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.revision() != expectedRevision) {
                throw new MxzApplicationException("REVISION_CONFLICT", "definition revision mismatch");
            }
            if (def.controlState() == ControlState.RETIRED) {
                throw new MxzApplicationException("INVALID_STATE", "RETIRED cannot update");
            }

            ParsedUpdate parsed = parseUpdatePayload(payload, def.scenarioKey(), def.scenarioSchemaVersion());
            var ext = extensionRegistry
                    .scenarioExtensions()
                    .require(new ScenarioExtensionKey(def.scenarioKey(), 1));
            ext.validateDefinitionConfig(new MxzDefinitionConfigValidationContext(
                    def.scenarioKey(),
                    parsed.scenarioSchemaVersion(),
                    new MxzJsonPayload(parsed.scenarioConfig())));
            validateParticipants(actor.principalId(), parsed.participants());
            validateCalendarBinding(parsed.triggerBindings());

            var now = clock.nowUtcSeconds();
            var result = definitionUpdatePort.apply(new DefinitionUpdatePort.UpdateCommand(
                    definitionId,
                    actor.tenantKey(),
                    actor.principalType(),
                    actor.principalId(),
                    requestId,
                    requestId.replace("-", ""),
                    expectedRevision,
                    parsed.scenarioSchemaVersion(),
                    parsed.title(),
                    parsed.description(),
                    parsed.scenarioConfigJson(),
                    parsed.participants(),
                    parsed.triggerBindings(),
                    now,
                    now.plus(7, ChronoUnit.DAYS)));

            commandDedupRepository.complete(
                    actor.tenantKey(),
                    actor.principalId(),
                    op,
                    requestId,
                    result.changed() ? "APPLIED" : "NO_CHANGE",
                    "DEFINITION",
                    String.valueOf(definitionId),
                    result.revision(),
                    "{\"definitionId\":" + definitionId + ",\"changed\":" + result.changed() + "}");
            return new CommandResult("update", definitionId, result.revision(), result.changed());
        });
    }

    private CommandResult executeControl(
            long definitionId,
            String commandKey,
            String requestId,
            long expectedRevision,
            int commandSchemaVersion,
            JsonNode payload) {
        if (commandSchemaVersion != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "commandSchemaVersion must be 1");
        }
        if (payload == null || !payload.isObject() || !payload.isEmpty()) {
            throw new MxzApplicationException("INVALID_REQUEST", "control command payload must be empty object");
        }
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions/" + definitionId + "/commands/" + commandKey;
        byte[] hash = MxzSha256.digestUtf8(requestId + ":" + commandKey);

        return tx.execute(() -> {
            var completed = commandDedupRepository.findCompletedResponseJson(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (completed.isPresent()) {
                var def = definitionRepository
                        .findById(definitionId)
                        .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
                return new CommandResult(commandKey, definitionId, def.revision(), false);
            }
            boolean acquired = commandDedupRepository.tryBegin(
                    actor.tenantKey(), actor.principalId(), op, requestId, hash);
            if (!acquired) {
                var again = commandDedupRepository.findCompletedResponseJson(
                        actor.tenantKey(), actor.principalId(), op, requestId);
                if (again.isPresent()) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new CommandResult(commandKey, definitionId, def.revision(), false);
                }
                throw new MxzApplicationException("RETRY_LATER", "dedup in progress");
            }

            var def = definitionRepository
                    .findByIdForUpdate(definitionId)
                    .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
            if (def.revision() != expectedRevision) {
                throw new MxzApplicationException("REVISION_CONFLICT", "definition revision mismatch");
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
                return new CommandResult(commandKey, definitionId, def.revision(), false);
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

            var result = committer.commit(new MxzTransitionCommitRequest(
                    plan, definitionId, null, "COMMAND", commandKey));

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
            return new CommandResult(commandKey, definitionId, result.toRevision(), true);
        });
    }

    private ParsedUpdate parseUpdatePayload(JsonNode payload, String scenarioKey, int currentSchemaVersion) {
        if (payload == null || !payload.isObject()) {
            throw new MxzApplicationException("INVALID_REQUEST", "update payload must be object");
        }
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = payload.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        if (names.size() != UPDATE_FIELDS.size() || !UPDATE_FIELDS.containsAll(names)) {
            throw new MxzApplicationException(
                    "INVALID_REQUEST", "update payload must provide exactly six required fields");
        }
        for (String required : UPDATE_FIELDS) {
            if (!payload.has(required)) {
                throw new MxzApplicationException("INVALID_REQUEST", "missing field: " + required);
            }
            if (!"description".equals(required) && payload.get(required).isNull()) {
                throw new MxzApplicationException("INVALID_REQUEST", required + " cannot be null");
            }
        }

        if (!payload.get("scenarioSchemaVersion").canConvertToInt()) {
            throw new MxzApplicationException("INVALID_REQUEST", "scenarioSchemaVersion must be int");
        }
        int schemaVersion = payload.get("scenarioSchemaVersion").asInt();
        if (schemaVersion != currentSchemaVersion) {
            throw new MxzApplicationException("INVALID_REQUEST", "scenarioSchemaVersion cannot change via update");
        }
        if (!payload.get("title").isTextual()) {
            throw new MxzApplicationException("INVALID_REQUEST", "title must be string");
        }
        String title = payload.get("title").asText();
        String description = payload.get("description").isNull() ? null : payload.get("description").asText();
        if (!payload.get("description").isNull() && !payload.get("description").isTextual()) {
            throw new MxzApplicationException("INVALID_REQUEST", "description must be string or null");
        }
        if (!payload.get("scenarioConfig").isObject()) {
            throw new MxzApplicationException("INVALID_REQUEST", "scenarioConfig must be object");
        }
        if (!payload.get("participants").isArray()) {
            throw new MxzApplicationException("INVALID_REQUEST", "participants must be array");
        }
        if (!payload.get("triggerBindings").isArray()) {
            throw new MxzApplicationException("INVALID_REQUEST", "triggerBindings must be array");
        }

        Map<String, Object> scenarioConfig = objectMapper.convertValue(payload.get("scenarioConfig"), Map.class);
        if ("recurring_todo".equals(scenarioKey)) {
            scenarioConfig = expandRecurringTodoDefaults(scenarioConfig);
        }
        String scenarioConfigJson;
        try {
            scenarioConfigJson = objectMapper.writeValueAsString(scenarioConfig);
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid scenarioConfig");
        }

        List<MxzCreateDefinitionCommand.ParticipantInput> participants = new ArrayList<>();
        for (JsonNode p : payload.get("participants")) {
            if (!p.isObject()
                    || !p.hasNonNull("principalType")
                    || !p.hasNonNull("principalId")
                    || !p.hasNonNull("roleCode")) {
                throw new MxzApplicationException("INVALID_REQUEST", "invalid participant");
            }
            participants.add(new MxzCreateDefinitionCommand.ParticipantInput(
                    p.get("principalType").asText(),
                    p.get("principalId").asText(),
                    p.get("roleCode").asText()));
        }

        List<MxzCreateDefinitionCommand.TriggerBindingInput> bindings = new ArrayList<>();
        for (JsonNode b : payload.get("triggerBindings")) {
            if (!b.isObject()
                    || !b.hasNonNull("bindingKey")
                    || !b.hasNonNull("providerKey")
                    || !b.hasNonNull("schemaVersion")
                    || !b.has("config")
                    || b.get("config").isNull()) {
                throw new MxzApplicationException("INVALID_REQUEST", "invalid triggerBinding");
            }
            if (!b.get("config").isObject()) {
                throw new MxzApplicationException("INVALID_REQUEST", "trigger config must be object");
            }
            bindings.add(new MxzCreateDefinitionCommand.TriggerBindingInput(
                    b.get("bindingKey").asText(),
                    b.get("providerKey").asText(),
                    b.get("schemaVersion").asInt(),
                    b.get("config").toString()));
        }

        return new ParsedUpdate(
                schemaVersion, title, description, scenarioConfig, scenarioConfigJson, participants, bindings);
    }

    private Map<String, Object> expandRecurringTodoDefaults(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "chaseOffsetsMinutes",
                raw.containsKey("chaseOffsetsMinutes")
                        ? raw.get("chaseOffsetsMinutes")
                        : List.of(60, 240, 720));
        out.put(
                "notificationExpireAfterMinutes",
                raw.containsKey("notificationExpireAfterMinutes")
                        ? raw.get("notificationExpireAfterMinutes")
                        : 1440);
        out.put(
                "maxSnoozeCount",
                raw.containsKey("maxSnoozeCount") ? raw.get("maxSnoozeCount") : 3);
        for (String key : raw.keySet()) {
            if (!out.containsKey(key)) {
                throw new MxzApplicationException("INVALID_REQUEST", "unknown scenarioConfig field: " + key);
            }
        }
        return out;
    }

    private void validateCalendarBinding(List<MxzCreateDefinitionCommand.TriggerBindingInput> bindings) {
        if (bindings.size() != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        var tb = bindings.get(0);
        if (!"calendar".equals(tb.providerKey()) || !"primary".equals(tb.bindingKey())) {
            throw new MxzApplicationException("INVALID_REQUEST", "expected calendar/primary binding");
        }
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(tb.configJson(), Map.class);
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
        try {
            MxzCalendarConfigParser.parse(config);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new MxzApplicationException("INVALID_REQUEST", msg);
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

    private ControlState resolveTarget(String commandKey, ControlState current) {
        return switch (commandKey) {
            case "pause" -> {
                if (current == ControlState.ACTIVE) yield ControlState.PAUSED;
                if (current == ControlState.PAUSED) yield ControlState.PAUSED;
                throw new MxzApplicationException("INVALID_STATE", "pause requires ACTIVE, got " + current);
            }
            case "resume" -> {
                if (current == ControlState.PAUSED) yield ControlState.ACTIVE;
                if (current == ControlState.ACTIVE) yield ControlState.ACTIVE;
                throw new MxzApplicationException("INVALID_STATE", "resume requires PAUSED, got " + current);
            }
            case "retire" -> {
                if (current == ControlState.RETIRED) yield ControlState.RETIRED;
                yield ControlState.RETIRED;
            }
            default -> throw new MxzApplicationException(
                    "COMMAND_NOT_SUPPORTED", "definition command '" + commandKey + "' not supported");
        };
    }

    private record ParsedUpdate(
            int scenarioSchemaVersion,
            String title,
            String description,
            Map<String, Object> scenarioConfig,
            String scenarioConfigJson,
            List<MxzCreateDefinitionCommand.ParticipantInput> participants,
            List<MxzCreateDefinitionCommand.TriggerBindingInput> triggerBindings) {}
}
