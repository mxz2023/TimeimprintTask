package cn.net.mxz.timeimprint.task.domain.shared.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommandResultView(
        @JsonProperty("resourceType") String resourceType,
        @JsonProperty("resourceId") String resourceId,
        @JsonProperty("resourceRevision") long resourceRevision,
        @JsonProperty("changed") boolean changed,
        @JsonProperty("resourceSnapshot") JsonNode resourceSnapshot,
        @JsonProperty("scenarioResult") JsonNode scenarioResult) {}
