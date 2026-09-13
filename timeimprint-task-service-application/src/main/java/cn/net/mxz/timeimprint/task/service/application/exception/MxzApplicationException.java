package cn.net.mxz.timeimprint.task.service.application.exception;

public class MxzApplicationException extends RuntimeException {

    private final String errorCode;

    public MxzApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MxzApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
