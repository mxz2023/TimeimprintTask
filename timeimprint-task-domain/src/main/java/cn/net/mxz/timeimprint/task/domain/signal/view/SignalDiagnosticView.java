package cn.net.mxz.timeimprint.task.domain.signal.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SignalDiagnosticView(
        @JsonProperty("signalId") String signalId,
        @JsonProperty("definitionId") String definitionId,
        @JsonProperty("instanceId") String instanceId,
        @JsonProperty("providerKey") String providerKey,
        @JsonProperty("signalKey") String signalKey,
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("processStatus") String processStatus,
        @JsonProperty("attemptCount") int attemptCount,
        @JsonProperty("maxAttempts") int maxAttempts,
        @JsonProperty("nextAttemptAt") String nextAttemptAt,
        @JsonProperty("leaseOwner") String leaseOwner,
        @JsonProperty("leaseUntil") String leaseUntil,
        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultSummary") String resultSummary,
        @JsonProperty("occurredAt") String occurredAt,
        @JsonProperty("receivedAt") String receivedAt,
        @JsonProperty("processedAt") String processedAt,
        @JsonProperty("parentSignalId") String parentSignalId,
        @JsonProperty("redriveNo") int redriveNo) {}
