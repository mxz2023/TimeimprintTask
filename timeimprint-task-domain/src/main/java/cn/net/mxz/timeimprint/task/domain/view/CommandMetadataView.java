package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommandMetadataView(
        @JsonProperty("commandKey") String commandKey,
        @JsonProperty("supportedCommandSchemaVersions") List<Integer> supportedCommandSchemaVersions) {}
