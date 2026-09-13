package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator;
import cn.net.mxz.timeimprint.task.service.extension.context.MxzDefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ExtensionRegistry;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.MxzJsonPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MxzPreviewService {

    public record Occurrence(String occurrenceKey, Instant occurrenceAt, Instant dueAt) {}

    public record PreviewOutcome(
            String scenarioKey,
            int scenarioSchemaVersion,
            String normalizedScenarioConfigJson,
            List<Map<String, Object>> normalizedTriggerBindings,
            List<Occurrence> occurrences) {}

    private final ExtensionRegistry extensionRegistry;
    private final ObjectMapper objectMapper;

    public MxzPreviewService(ExtensionRegistry extensionRegistry, ObjectMapper objectMapper) {
        this.extensionRegistry = extensionRegistry;
        this.objectMapper = objectMapper;
    }

    public PreviewOutcome preview(
            String scenarioKey,
            int scenarioSchemaVersion,
            String scenarioConfigJson,
            List<Map<String, Object>> triggerBindings,
            Instant after,
            int limit) {
        var ext = extensionRegistry.scenarioExtensions().require(new ScenarioExtensionKey(scenarioKey, 1));
        if (scenarioSchemaVersion != 1) {
            throw new MxzApplicationException(
                    "UNSUPPORTED_SCHEMA_VERSION", "scenarioSchemaVersion " + scenarioSchemaVersion);
        }
        Map<String, Object> scenarioFields = parse(scenarioConfigJson);
        try {
            ext.validateDefinitionConfig(new MxzDefinitionConfigValidationContext(
                    scenarioKey, scenarioSchemaVersion, new MxzJsonPayload(scenarioFields)));
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid scenarioConfig" : ex.getMessage();
            throw new MxzApplicationException("INVALID_REQUEST", msg);
        }
        if (triggerBindings.size() != 1) {
            throw new MxzApplicationException("INVALID_REQUEST", "exactly one trigger binding required");
        }
        Map<String, Object> binding = triggerBindings.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) binding.get("config");
        final MxzCalendarOccurrenceCalculator.Rule rule;
        try {
            rule = MxzCalendarConfigParser.parse(config);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "invalid calendar config" : ex.getMessage();
            if (msg.startsWith("INVALID_REQUEST:")) {
                msg = msg.substring("INVALID_REQUEST:".length()).trim();
            }
            throw new MxzApplicationException("INVALID_REQUEST", msg);
        }
        var occs = MxzCalendarOccurrenceCalculator.preview(rule, after, limit).stream()
                .map(o -> {
                    // docs/04-API.md: S01 dueAt=null；S02 dueAt=occurrenceAt
                    Instant dueAt = "recurring_todo".equals(scenarioKey) ? o.occurrenceAt() : null;
                    return new Occurrence(o.occurrenceKey(), o.occurrenceAt(), dueAt);
                })
                .toList();
        return new PreviewOutcome(scenarioKey, scenarioSchemaVersion, scenarioConfigJson, triggerBindings, occs);
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid json");
        }
    }
}
