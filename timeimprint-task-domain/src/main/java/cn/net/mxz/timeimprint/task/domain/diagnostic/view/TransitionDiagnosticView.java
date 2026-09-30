package cn.net.mxz.timeimprint.task.domain.diagnostic.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Internal transition diagnostic view (I05) from {@code docs/04-API.md} section 3.5. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TransitionDiagnosticView(
        @JsonProperty("transitionId") String transitionId,
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("sourceKey") String sourceKey,
        @JsonProperty("commandKey") String commandKey,
        @JsonProperty("fromControlState") String fromControlState,
        @JsonProperty("toControlState") String toControlState,
        @JsonProperty("fromLifecycle") String fromLifecycle,
        @JsonProperty("toLifecycle") String toLifecycle,
        @JsonProperty("fromScenarioState") String fromScenarioState,
        @JsonProperty("toScenarioState") String toScenarioState,
        @JsonProperty("fromRevision") long fromRevision,
        @JsonProperty("toRevision") long toRevision,
        @JsonProperty("actorType") String actorType,
        @JsonProperty("actorId") String actorId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("createdAt") String createdAt) {}
