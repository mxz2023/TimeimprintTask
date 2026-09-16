package cn.net.mxz.timeimprint.task.domain.shared.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedriveRequest(
        @JsonProperty("requestId") @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                String requestId,
        @JsonProperty("expectedStatus") @NotBlank String expectedStatus,
        @JsonProperty("reason") @NotBlank @Size(min = 1, max = 500) String reason) {}
