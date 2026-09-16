package cn.net.mxz.timeimprint.task.domain.shared.response;

import java.util.UUID;

/** Helpers for building {@link ApiResponse} envelopes with Chinese messages. */
public final class ApiResponses {

    private ApiResponses() {}

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(ApiErrorCodes.OK, ApiMessages.ok(message), traceId(), data);
    }

    public static <T> ApiResponse<T> error(String code, String detail, T data) {
        return new ApiResponse<>(code, ApiMessages.error(code, detail), traceId(), data);
    }

    public static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
