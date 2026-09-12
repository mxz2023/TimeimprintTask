package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SignalAcceptedView(
        @JsonProperty("signalId") String signalId,
        @JsonProperty("duplicated") boolean duplicated,
        @JsonProperty("processStatus") String processStatus,
        @JsonProperty("receivedAt") String receivedAt) {}
