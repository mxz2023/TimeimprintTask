package cn.net.mxz.timeimprint.task.domain.instance.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.util.List;
import cn.net.mxz.timeimprint.task.domain.shared.view.DeliverySummary;
import cn.net.mxz.timeimprint.task.domain.shared.view.ParticipantView;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskInstanceView(
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("scenarioKey") String scenarioKey,
        @JsonProperty("scenarioSchemaVersion") int scenarioSchemaVersion,
        @JsonProperty("lifecycleCategory") String lifecycleCategory,
        @JsonProperty("scenarioState") String scenarioState,
        @JsonProperty("revision") long revision,
        @JsonProperty("titleSnapshot") String titleSnapshot,
        @JsonProperty("descriptionSnapshot") String descriptionSnapshot,
        @JsonProperty("participants") List<ParticipantView> participants,
        @JsonProperty("occurrenceAt") String occurrenceAt,
        @JsonProperty("dueAt") String dueAt,
        @JsonProperty("allowedCommands") List<String> allowedCommands,
        @JsonProperty("scenarioProjection") JsonNode scenarioProjection,
        @JsonProperty("deliverySummary") DeliverySummary deliverySummary,
        @JsonProperty("createdAt") String createdAt,
        @JsonProperty("updatedAt") String updatedAt,
        @JsonProperty("terminalAt") String terminalAt) {}
