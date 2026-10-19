package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

/**
 * calendar schemaVersion 3 的请求拒绝。{@code message} 以原因键加全角冒号开头。
 * 场景不接受版本 3 时 {@code errorCode} 为 UNSUPPORTED_SCHEMA_VERSION。
 */
public final class CalendarConfigException extends RuntimeException {

    private final String errorCode;

    public CalendarConfigException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public static CalendarConfigException invalid(String reasonKey, String detail) {
        return new CalendarConfigException("INVALID_REQUEST", reasonKey + "：" + detail);
    }

    public static CalendarConfigException unsupportedSchema() {
        return new CalendarConfigException(
                "UNSUPPORTED_SCHEMA_VERSION", "calendar schemaVersion 3 is not accepted by this scenario");
    }
}
