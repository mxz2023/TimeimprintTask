package cn.net.mxz.timeimprint.task.identity.account.service;

/** 可返回给调用方的账号错误。errorCode 与平台 HTTP 错误码一致。 */
public class IdentityException extends RuntimeException {

    private final String errorCode;

    public IdentityException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
