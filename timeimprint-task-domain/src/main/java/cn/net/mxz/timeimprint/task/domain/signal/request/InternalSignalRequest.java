package cn.net.mxz.timeimprint.task.domain.signal.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InternalSignalRequest(
        @JsonProperty("requestId") @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                String requestId,
        @JsonProperty("signalKey") @NotBlank @Size(max = 256) String signalKey,
        @JsonProperty("schemaVersion") @Min(1) int schemaVersion,
        @JsonProperty("occurredAt") @NotBlank String occurredAt,
        @JsonProperty("subject") @NotNull @Valid SignalSubjectInput subject,
        @JsonProperty("payload") @NotNull JsonNode payload) {}
