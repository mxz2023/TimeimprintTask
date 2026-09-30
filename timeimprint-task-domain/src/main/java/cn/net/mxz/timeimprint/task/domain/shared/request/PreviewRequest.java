package cn.net.mxz.timeimprint.task.domain.shared.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PreviewRequest(
        @JsonProperty("scenarioKey")
                @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}")
                String scenarioKey,
        @JsonProperty("scenarioSchemaVersion") @NotNull Integer scenarioSchemaVersion,
        @JsonProperty("triggerBindings") @NotEmpty @Valid List<TriggerBindingInput> triggerBindings,
        @JsonProperty("scenarioConfig") @NotNull JsonNode scenarioConfig,
        @JsonProperty("after") @NotBlank String after,
        @JsonProperty("limit") @Min(1) @Max(100) int limit) {}
