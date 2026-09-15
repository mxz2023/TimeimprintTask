package cn.net.mxz.timeimprint.task.service.application.limit;

import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.CreateDefinitionCommand;
import cn.net.mxz.timeimprint.task.service.application.recipient.RecipientRules;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarConfigParser;
import cn.net.mxz.timeimprint.task.service.capability.calendar.CalendarOccurrenceCalculator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pre-write validation for definition create (A25/A29). */
public final class CreateWriteLimitsValidator {

    private CreateWriteLimitsValidator() {}

    public static void validateBeforeWrite(
            String description,
            String scenarioConfigJson,
            List<CreateDefinitionCommand.ParticipantInput> participants,
            List<CreateDefinitionCommand.TriggerBindingInput> triggerBindings,
            Instant now,
            Instant windowEnd,
            ObjectMapper objectMapper) {
        if (participants.size() > PlatformLimits.MAX_PARTICIPANTS_PER_SCOPE) {
            throw limit("参与人数量超过上限，请减少 participants 后重试");
        }
        if (triggerBindings.size() > PlatformLimits.MAX_TRIGGER_BINDINGS) {
            throw limit("触发绑定数量超过上限");
        }
        if (Utf8LimitUtils.utf8ByteLength(description) > PlatformLimits.MAX_JSON_VALUE_BYTES) {
            throw limit("description 体积超过单 JSON 上限");
        }
        if (Utf8LimitUtils.utf8ByteLength(scenarioConfigJson) > PlatformLimits.MAX_JSON_VALUE_BYTES) {
            throw limit("scenarioConfig 体积超过单 JSON 上限");
        }
        RecipientRules.resolveFromInputs(
                participants.stream()
                        .map(p -> new RecipientRules.ParticipantRef(p.principalId(), p.roleCode()))
                        .toList());
        validateOccurrenceWindow(triggerBindings, now, windowEnd, objectMapper);
    }

    private static void validateOccurrenceWindow(
            List<CreateDefinitionCommand.TriggerBindingInput> triggerBindings,
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
            CalendarOccurrenceCalculator.Rule rule;
            try {
                rule = CalendarConfigParser.parse(configMap);
            } catch (IllegalArgumentException ex) {
                return; // scenario-specific validation will surface parse errors
            }
            var preview = CalendarOccurrenceCalculator.preview(
                    rule, now, PlatformLimits.MAX_OCCURRENCES_PER_WRITE_TX);
            List<CalendarOccurrenceCalculator.Occurrence> window = new ArrayList<>();
            for (var occ : preview) {
                if (!occ.occurrenceAt().isAfter(windowEnd)) {
                    window.add(occ);
                }
            }
            totalOccurrences += window.size();
        }
        if (totalOccurrences > PlatformLimits.MAX_OCCURRENCES_PER_WRITE_TX) {
            throw limit("规划窗口内发生次数超过上限");
        }
    }

    private static Map<String, Object> parseMap(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ApplicationException("INVALID_REQUEST", "触发绑定 config 不合法");
        }
    }

    private static ApplicationException limit(String detail) {
        return new ApplicationException("INVALID_REQUEST", detail);
    }
}
