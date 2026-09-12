package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScenarioMetadataView(
        @JsonProperty("scenarioKey") String scenarioKey,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("contractVersion") String contractVersion,
        @JsonProperty("supportedScenarioSchemaVersions") List<Integer> supportedScenarioSchemaVersions,
        @JsonProperty("definitionCommands") List<CommandMetadataView> definitionCommands,
        @JsonProperty("instanceCommands") List<CommandMetadataView> instanceCommands,
        @JsonProperty("requiredCapabilities") List<String> requiredCapabilities) {}
