package cn.net.mxz.timeimprint.task.domain;

import java.util.UUID;

/** Helpers for building {@link MxzApiResponse} envelopes with Chinese messages. */
public final class MxzApiResponses {

    private MxzApiResponses() {}

    public static <T> MxzApiResponse<T> ok(String message, T data) {
        return new MxzApiResponse<>(MxzApiErrorCodes.OK, MxzApiMessages.ok(message), traceId(), data);
    }

    public static <T> MxzApiResponse<T> error(String code, String detail, T data) {
        return new MxzApiResponse<>(code, MxzApiMessages.error(code, detail), traceId(), data);
    }

    public static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
