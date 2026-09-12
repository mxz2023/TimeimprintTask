package cn.net.mxz.timeimprint.task.domain.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ParticipantView(
        @JsonProperty("principalType") String principalType,
        @JsonProperty("principalId") String principalId,
        @JsonProperty("roleCode") String roleCode,
        @JsonProperty("sourceCode") String sourceCode) {}
