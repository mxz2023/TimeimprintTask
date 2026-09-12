package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AttemptSummary(
        @JsonProperty("attemptNo") int attemptNo,
        @JsonProperty("startedAt") String startedAt,
        @JsonProperty("finishedAt") String finishedAt,
        @JsonProperty("effectStarted") boolean effectStarted,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("errorClass") String errorClass,
        @JsonProperty("errorCode") String errorCode,
        @JsonProperty("providerReference") String providerReference,
        @JsonProperty("safeSummary") String safeSummary) {}
