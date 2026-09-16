package cn.net.mxz.timeimprint.task.service.application.definition.model;

import java.time.Instant;
import java.util.List;


public record CreateDefinitionCommand(
        String tenantId,
        String actorType,
        String actorId,
        String requestId,
        String scenarioKey,
        int scenarioSchemaVersion,
        String title,
        String description,
        String scenarioConfigJson,
        List<ParticipantInput> participants,
        List<TriggerBindingInput> triggerBindings,
        Instant now,
        Instant windowEnd,
        String traceId) {

    public record ParticipantInput(String principalType, String principalId, String roleCode) {}

    public record TriggerBindingInput(
            String bindingKey, String providerKey, int schemaVersion, String configJson) {}
}
