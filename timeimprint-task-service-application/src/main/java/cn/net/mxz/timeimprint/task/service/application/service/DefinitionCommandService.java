package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.Sha256;
import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionControlPort;
import cn.net.mxz.timeimprint.task.service.application.port.DefinitionUpdatePort;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.validation.ParticipantCreateValidator;
import cn.net.mxz.timeimprint.task.service.application.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.extension.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
public class DefinitionCommandService {

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
    private final ParticipantCreateValidator participantCreateValidator;

    public DefinitionCommandService(
            ActorContextProvider actorContextProvider,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            DefinitionControlPort definitionControlPort,
            DefinitionUpdatePort definitionUpdatePort,
            ExtensionRegistry extensionRegistry,
            TransitionPlanCommitter committer,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper,
            ParticipantCreateValidator participantCreateValidator) {
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
        this.participantCreateValidator = participantCreateValidator;
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
            throw new ApplicationException("INVALID_REQUEST", "commandSchemaVersion must be 1");
        }
        var actor = actorContextProvider.requireCurrentActor();
        String op = "POST /api/v1/task-definitions/" + definitionId + "/commands/update";
        byte[] hash = Sha256.digestUtf8(requestId + ":update:" + payload);

        return tx.execute(() -> {
            var existing = commandDedupRepository.find(
                    actor.tenantKey(), actor.principalId(), op, requestId);
            if (existing.isPresent()) {
                assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new CommandResult("update", definitionId, def.revision(), false);
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
                        var def = definitionRepository
                                .findById(definitionId)
                                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                        return new CommandResult("update", definitionId, def.revision(), false);
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
            if (def.controlState() == ControlState.RETIRED) {
                throw new ApplicationException("INVALID_STATE", "RETIRED cannot update");
            }

            ParsedUpdate parsed = parseUpdatePayload(payload, def.scenarioKey(), def.scenarioSchemaVersion());
            Map<String, Object> rawScenarioConfig =
                    objectMapper.convertValue(payload.get("scenarioConfig"), Map.class);
            var ext = extensionRegistry
                    .scenarioExtensions()
                    .require(new ScenarioExtensionKey(def.scenarioKey(), 1));
            try {
                ext.validateDefinitionConfig(new DefinitionConfigValidationContext(
                        def.scenarioKey(),
                        parsed.scenarioSchemaVersion(),
                        new JsonPayload(rawScenarioConfig)));
            } catch (IllegalArgumentException ex) {
                String msg = ex.getMessage() == null ? "invalid scenarioConfig" : ex.getMessage();
                throw new ApplicationException("INVALID_REQUEST", msg);
            }
            participantCreateValidator.validateParticipants(actor.principalId(), parsed.participants());
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
                assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new CommandResult(commandKey, definitionId, def.revision(), false);
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
                        var def = definitionRepository
                                .findById(definitionId)
                                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                        return new CommandResult(commandKey, definitionId, def.revision(), false);
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
            return new CommandResult(commandKey, definitionId, result.toRevision(), true);
        });
    }

    private ParsedUpdate parseUpdatePayload(JsonNode payload, String scenarioKey, int currentSchemaVersion) {
        if (payload == null || !payload.isObject()) {
            throw new ApplicationException("INVALID_REQUEST", "update payload must be object");
        }
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = payload.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        if (names.size() != UPDATE_FIELDS.size() || !UPDATE_FIELDS.containsAll(names)) {
            throw new ApplicationException(
                    "INVALID_REQUEST", "update payload must provide exactly six required fields");
        }
        for (String required : UPDATE_FIELDS) {
            if (!payload.has(required)) {
                throw new ApplicationException("INVALID_REQUEST", "missing field: " + required);
            }
            if (!"description".equals(required) && payload.get(required).isNull()) {
                throw new ApplicationException("INVALID_REQUEST", required + " cannot be null");
            }
        }

        if (!payload.get("scenarioSchemaVersion").canConvertToInt()) {
            throw new ApplicationException("INVALID_REQUEST", "scenarioSchemaVersion must be int");
        }
        int schemaVersion = payload.get("scenarioSchemaVersion").asInt();
        if (schemaVersion != currentSchemaVersion) {
            throw new ApplicationException("INVALID_REQUEST", "scenarioSchemaVersion cannot change via update");
        }
        if (!payload.get("title").isTextual()) {
            throw new ApplicationException("INVALID_REQUEST", "title must be string");
        }
        String title = payload.get("title").asText();
        String description = payload.get("description").isNull() ? null : payload.get("description").asText();
        if (!payload.get("description").isNull() && !payload.get("description").isTextual()) {
            throw new ApplicationException("INVALID_REQUEST", "description must be string or null");
        }
        if (!payload.get("scenarioConfig").isObject()) {
            throw new ApplicationException("INVALID_REQUEST", "scenarioConfig must be object");
        }
        if (!payload.get("participants").isArray()) {
            throw new ApplicationException("INVALID_REQUEST", "participants must be array");
        }
        if (!payload.get("triggerBindings").isArray()) {
            throw new ApplicationException("INVALID_REQUEST", "triggerBindings must be array");
        }

        Map<String, Object> scenarioConfig = objectMapper.convertValue(payload.get("scenarioConfig"), Map.class);
        if ("recurring_todo".equals(scenarioKey)) {
            scenarioConfig = expandRecurringTodoDefaults(scenarioConfig);
        }
        String scenarioConfigJson;
        try {
            scenarioConfigJson = objectMapper.writeValueAsString(scenarioConfig);
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "invalid scenarioConfig");
        }

        List<CreateDefinitionCommand.ParticipantInput> participants = new ArrayList<>();
        for (JsonNode p : payload.get("participants")) {
            if (!p.isObject()
                    || !p.hasNonNull("principalType")
                    || !p.hasNonNull("principalId")
                    || !p.hasNonNull("roleCode")) {
                throw new ApplicationException("INVALID_REQUEST", "invalid participant");
            }
            participants.add(new CreateDefinitionCommand.ParticipantInput(
                    p.get("principalType").asText(),
                    p.get("principalId").asText(),
                    p.get("roleCode").asText()));
        }

        List<CreateDefinitionCommand.TriggerBindingInput> bindings = new ArrayList<>();
        for (JsonNode b : payload.get("triggerBindings")) {
            if (!b.isObject()
                    || !b.hasNonNull("bindingKey")
                    || !b.hasNonNull("providerKey")
                    || !b.hasNonNull("schemaVersion")
                    || !b.has("config")
                    || b.get("config").isNull()) {
                throw new ApplicationException("INVALID_REQUEST", "invalid triggerBinding");
            }
            if (!b.get("config").isObject()) {
                throw new ApplicationException("INVALID_REQUEST", "trigger config must be object");
            }
            bindings.add(new CreateDefinitionCommand.TriggerBindingInput(
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
                throw new ApplicationException("INVALID_REQUEST", "unknown scenarioConfig field: " + key);
            }
        }
        return out;
    }

    private void validateCalendarBinding(List<CreateDefinitionCommand.TriggerBindingInput> bindings) {
        if (bindings.size() != 1) {
            throw new ApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        var tb = bindings.get(0);
        if (!"calendar".equals(tb.providerKey()) || !"primary".equals(tb.bindingKey())) {
            throw new ApplicationException("INVALID_REQUEST", "expected calendar/primary binding");
        }
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(tb.configJson(), Map.class);
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "invalid trigger config json");
        }
        try {
            CalendarConfigParser.parse(config);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new ApplicationException("INVALID_REQUEST", msg);
        }
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

    private void assertSameRequestHash(byte[] stored, byte[] incoming) {
        if (stored != null && !java.util.Arrays.equals(stored, incoming)) {
            throw new ApplicationException("IDEMPOTENCY_CONFLICT", "same requestId different payload");
        }
    }

    private record ParsedUpdate(
            int scenarioSchemaVersion,
            String title,
            String description,
            Map<String, Object> scenarioConfig,
            String scenarioConfigJson,
            List<CreateDefinitionCommand.ParticipantInput> participants,
            List<CreateDefinitionCommand.TriggerBindingInput> triggerBindings) {}
}
