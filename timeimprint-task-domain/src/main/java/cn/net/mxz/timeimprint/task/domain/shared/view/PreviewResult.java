package cn.net.mxz.timeimprint.task.domain.shared.view;

import cn.net.mxz.timeimprint.task.domain.shared.request.TriggerBindingInput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PreviewResult(
        @JsonProperty("scenarioKey") String scenarioKey,
        @JsonProperty("scenarioSchemaVersion") int scenarioSchemaVersion,
        @JsonProperty("normalizedTriggerBindings") List<TriggerBindingInput> normalizedTriggerBindings,
        @JsonProperty("normalizedScenarioConfig") JsonNode normalizedScenarioConfig,
        @JsonProperty("occurrences") List<OccurrenceView> occurrences) {}
