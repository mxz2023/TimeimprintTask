package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskDefinitionView(
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("scenarioKey") String scenarioKey,
        @JsonProperty("scenarioSchemaVersion") int scenarioSchemaVersion,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("controlState") String controlState,
        @JsonProperty("controlGeneration") long controlGeneration,
        @JsonProperty("revision") long revision,
        @JsonProperty("participants") List<ParticipantView> participants,
        @JsonProperty("triggerBindings") List<TriggerBindingView> triggerBindings,
        @JsonProperty("scenarioConfig") JsonNode scenarioConfig,
        @JsonProperty("allowedCommands") List<String> allowedCommands,
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("updatedAt") String updatedAt,
        @JsonProperty("pausedAt") String pausedAt,
        @JsonProperty("retiredAt") String retiredAt) {}
