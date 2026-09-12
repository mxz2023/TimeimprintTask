package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommandResultView(
        @JsonProperty("resourceType") String resourceType,
        @JsonProperty("resourceId") String resourceId,
        @JsonProperty("resourceRevision") long resourceRevision,
        @JsonProperty("changed") boolean changed,
        @JsonProperty("resourceSnapshot") JsonNode resourceSnapshot,
        @JsonProperty("scenarioResult") JsonNode scenarioResult) {}
