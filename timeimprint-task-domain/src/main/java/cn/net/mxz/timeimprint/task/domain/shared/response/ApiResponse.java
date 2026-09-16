package cn.net.mxz.timeimprint.task.domain.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Unified HTTP response envelope from {@code docs/04-API.md} section 2.
 *
 * @param <T> successful payload type; {@code null} on error responses
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("data") T data) {}
