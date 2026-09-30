package cn.net.mxz.timeimprint.task.domain.signal.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Signal target subject for I01 ({@code docs/04-API.md} section 5). */
public record SignalSubjectInput(
        @JsonProperty("definitionId") @NotBlank String definitionId,
        @JsonProperty("instanceId") String instanceId) {}
