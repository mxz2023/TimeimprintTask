package cn.net.mxz.timeimprint.task.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipantInput(
        @JsonProperty("principalType") @NotBlank @Size(max = 64) String principalType,
        @JsonProperty("principalId") @NotBlank @Size(max = 128) String principalId,
        @JsonProperty("roleCode") @NotBlank @Size(max = 64) String roleCode) {}
