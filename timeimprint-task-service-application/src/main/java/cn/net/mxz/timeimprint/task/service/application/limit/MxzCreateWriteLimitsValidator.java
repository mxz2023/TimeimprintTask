package cn.net.mxz.timeimprint.task.service.application.limit;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzCreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.recipient.MxzRecipientRules;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.MxzCalendarOccurrenceCalculator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pre-write validation for definition create (A25/A29). */
public final class MxzCreateWriteLimitsValidator {

    private MxzCreateWriteLimitsValidator() {}

    public static void validateBeforeWrite(
            String description,
            String scenarioConfigJson,
            List<MxzCreateDefinitionCommand.ParticipantInput> participants,
            List<MxzCreateDefinitionCommand.TriggerBindingInput> triggerBindings,
            Instant now,
            Instant windowEnd,
            ObjectMapper objectMapper) {
        if (participants.size() > MxzPlatformLimits.MAX_PARTICIPANTS_PER_SCOPE) {
            throw limit("too many participants");
        }
        if (triggerBindings.size() > MxzPlatformLimits.MAX_TRIGGER_BINDINGS) {
            throw limit("too many trigger bindings");
        }
        if (MxzUtf8LimitUtils.utf8ByteLength(description) > MxzPlatformLimits.MAX_JSON_VALUE_BYTES) {
            throw limit("description too large");
        }
        if (MxzUtf8LimitUtils.utf8ByteLength(scenarioConfigJson) > MxzPlatformLimits.MAX_JSON_VALUE_BYTES) {
            throw limit("scenarioConfig too large");
        }
        MxzRecipientRules.resolveFromInputs(
                participants.stream()
                        .map(p -> new MxzRecipientRules.ParticipantRef(p.principalId(), p.roleCode()))
                        .toList());
        validateOccurrenceWindow(triggerBindings, now, windowEnd, objectMapper);
    }

    private static void validateOccurrenceWindow(
            List<MxzCreateDefinitionCommand.TriggerBindingInput> triggerBindings,
            Instant now,
            Instant windowEnd,
            ObjectMapper objectMapper) {
        if (triggerBindings.isEmpty()) {
            return;
        }
        int totalOccurrences = 0;
        for (var tb : triggerBindings) {
            if (!"calendar".equals(tb.providerKey())) {
                continue;
            }
            Map<String, Object> configMap = parseMap(tb.configJson(), objectMapper);
            MxzCalendarOccurrenceCalculator.Rule rule;
            try {
                rule = MxzCalendarConfigParser.parse(configMap);
            } catch (IllegalArgumentException ex) {
                return; // scenario-specific validation will surface parse errors
            }
            var preview = MxzCalendarOccurrenceCalculator.preview(
                    rule, now, MxzPlatformLimits.MAX_OCCURRENCES_PER_WRITE_TX);
            List<MxzCalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
            for (var occ : preview) {
                if (!occ.occurrenceAt().isAfter(windowEnd)) {
                    window.add(occ);
                }
            }
            totalOccurrences += window.size();
        }
        if (totalOccurrences > MxzPlatformLimits.MAX_OCCURRENCES_PER_WRITE_TX) {
            throw limit("too many occurrences in planning window");
        }
    }

    private static Map<String, Object> parseMap(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new MxzApplicationException("INVALID_REQUEST", "invalid trigger binding config");
        }
    }

    private static MxzApplicationException limit(String detail) {
        return new MxzApplicationException("INVALID_REQUEST", detail);
    }
}
