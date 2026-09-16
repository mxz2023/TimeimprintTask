package cn.net.mxz.timeimprint.task.service.application.definition.service;

import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.definition.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.shared.port.CommandDedupRepository;
import cn.net.mxz.timeimprint.task.service.application.definition.port.DefinitionUpdatePort;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.transaction.TransactionBoundary;
import cn.net.mxz.timeimprint.task.service.application.shared.validation.ParticipantCreateValidator;
import cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.scenario.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
/**
 * Definition {@code update} command path (payload replace + calendar replan).
 * Change reason: update schema/defaults independent of pause/resume/retire control transitions.
 */
@Component
public class DefinitionUpdateExecutor {

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
    private final DefinitionUpdatePort definitionUpdatePort;
    private final ExtensionRegistry extensionRegistry;
    private final TransactionBoundary tx;
    private final BusinessClock clock;
    private final ObjectMapper objectMapper;
    private final ParticipantCreateValidator participantCreateValidator;

    public DefinitionUpdateExecutor(
            ActorContextProvider actorContextProvider,
            TaskDefinitionRepository definitionRepository,
            CommandDedupRepository commandDedupRepository,
            DefinitionUpdatePort definitionUpdatePort,
            ExtensionRegistry extensionRegistry,
            TransactionBoundary tx,
            BusinessClock clock,
            ObjectMapper objectMapper,
            ParticipantCreateValidator participantCreateValidator) {
        this.actorContextProvider = actorContextProvider;
        this.definitionRepository = definitionRepository;
        this.commandDedupRepository = commandDedupRepository;
        this.definitionUpdatePort = definitionUpdatePort;
        this.extensionRegistry = extensionRegistry;
        this.tx = tx;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.participantCreateValidator = participantCreateValidator;
    }

    public DefinitionCommandService.CommandResult execute(
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
                DefinitionCommandDedup.assertSameRequestHash(existing.get().requestHash(), hash);
                if ("COMPLETED".equals(existing.get().processStatus())) {
                    var def = definitionRepository
                            .findById(definitionId)
                            .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
                    return new DefinitionCommandService.CommandResult("update", definitionId, def.revision(), false);
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
                        return new DefinitionCommandService.CommandResult("update", definitionId, def.revision(), false);
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
            return new DefinitionCommandService.CommandResult("update", definitionId, result.revision(), result.changed());
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

    private record ParsedUpdate(
            int scenarioSchemaVersion,
            String title,
            String description,
            Map<String, Object> scenarioConfig,
            String scenarioConfigJson,
            List<CreateDefinitionCommand.ParticipantInput> participants,
            List<CreateDefinitionCommand.TriggerBindingInput> triggerBindings) {}
}
