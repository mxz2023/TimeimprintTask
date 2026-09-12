package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ActionJobDiagnosticView(
        @JsonProperty("actionJobId") String actionJobId,
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("transitionId") String transitionId,
        @JsonProperty("handlerKey") String handlerKey,
        @JsonProperty("executionMode") String executionMode,
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("targetType") String targetType,
        @JsonProperty("storedStatus") String storedStatus,
        @JsonProperty("effectiveStatus") String effectiveStatus,
        @JsonProperty("attemptCount") int attemptCount,
        @JsonProperty("maxAttempts") int maxAttempts,
        @JsonProperty("availableAt") String availableAt,
        @JsonProperty("expiresAt") String expiresAt,
        @JsonProperty("nextAttemptAt") String nextAttemptAt,
        @JsonProperty("leaseOwner") String leaseOwner,
        @JsonProperty("leaseUntil") String leaseUntil,
        @JsonProperty("outcomeCode") String outcomeCode,
        @JsonProperty("outcomeSummary") String outcomeSummary,
        @JsonProperty("completedAt") String completedAt,
        @JsonProperty("parentActionJobId") String parentActionJobId,
        @JsonProperty("redriveNo") int redriveNo,
        @JsonProperty("attempts") List<AttemptSummary> attempts) {}
