package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TriggerBindingView(
        @JsonProperty("bindingId") String bindingId,
        @JsonProperty("bindingKey") String bindingKey,
        @JsonProperty("providerKey") String providerKey,
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("config") JsonNode config,
        @JsonProperty("bindingState") String bindingState,
        @JsonProperty("scheduleGeneration") long scheduleGeneration,
        @JsonProperty("nextFireAt") String nextFireAt,
        @JsonProperty("exhausted") boolean exhausted,
        @JsonProperty("revision") long revision) {}
