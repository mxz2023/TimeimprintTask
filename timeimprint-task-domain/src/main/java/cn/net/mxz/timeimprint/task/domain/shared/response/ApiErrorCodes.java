package cn.net.mxz.timeimprint.task.domain.shared.response;

/**
 * HTTP API business error codes from {@code docs/04-API.md} section 7.
 */
public final class ApiErrorCodes {

    public static final String OK = "OK";

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_CURSOR = "INVALID_CURSOR";
    public static final String UNSUPPORTED_SCHEMA_VERSION = "UNSUPPORTED_SCHEMA_VERSION";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String EXTENSION_NOT_FOUND = "EXTENSION_NOT_FOUND";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String REVISION_CONFLICT = "REVISION_CONFLICT";
    public static final String STATE_CONFLICT = "STATE_CONFLICT";
    public static final String COMMAND_NOT_SUPPORTED = "COMMAND_NOT_SUPPORTED";
    public static final String REQUEST_TOO_LARGE = "REQUEST_TOO_LARGE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String POLICY_REJECTED = "POLICY_REJECTED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String RETRY_LATER = "RETRY_LATER";

    private ApiErrorCodes() {}
}
