package cn.net.mxz.timeimprint.task.domain.definition.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import cn.net.mxz.timeimprint.task.domain.shared.request.ParticipantInput;
import cn.net.mxz.timeimprint.task.domain.shared.request.TriggerBindingInput;

public record CreateTaskDefinitionRequest(
        @JsonProperty("requestId") @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                String requestId,
        @JsonProperty("scenarioKey")
                @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}")
                String scenarioKey,
        @JsonProperty("scenarioSchemaVersion") @NotNull Integer scenarioSchemaVersion,
        @JsonProperty("title") @NotBlank @Size(min = 1, max = 200) String title,
        @JsonProperty("description") @Size(max = 4000) String description,
        @JsonProperty("participants") @NotEmpty @Valid List<ParticipantInput> participants,
        @JsonProperty("triggerBindings") @NotEmpty @Valid List<TriggerBindingInput> triggerBindings,
        @JsonProperty("scenarioConfig") @NotNull JsonNode scenarioConfig) {}
