package cn.net.mxz.timeimprint.task.domain.definition.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DefinitionCommandRequest(
        @JsonProperty("requestId") @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                String requestId,
        @JsonProperty("expectedRevision") @Min(0) long expectedRevision,
        @JsonProperty("commandSchemaVersion") @Min(1) int commandSchemaVersion,
        @JsonProperty("payload") @NotNull JsonNode payload) {}
