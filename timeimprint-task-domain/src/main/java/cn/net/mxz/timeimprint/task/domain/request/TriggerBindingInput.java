package cn.net.mxz.timeimprint.task.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TriggerBindingInput(
        @JsonProperty("bindingKey")
                @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}")
                String bindingKey,
        @JsonProperty("providerKey")
                @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}")
                String providerKey,
        @JsonProperty("schemaVersion") @Min(1) int schemaVersion,
        @JsonProperty("config") @NotNull JsonNode config) {}
