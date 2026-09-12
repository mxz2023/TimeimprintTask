package cn.net.mxz.timeimprint.task.web.error;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MxzApiExceptionHandler {

    @ExceptionHandler(MxzApplicationException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleApp(MxzApplicationException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case "RESOURCE_NOT_FOUND", "EXTENSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "RETRY_LATER" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "IDEMPOTENCY_CONFLICT", "REVISION_CONFLICT", "STATE_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new MxzApiResponse<>(ex.errorCode(), ex.getMessage(), trace(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new MxzApiResponse<>(MxzApiErrorCodes.INVALID_REQUEST, "validation failed", trace(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MxzApiResponse<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MxzApiResponse<>(
                        MxzApiErrorCodes.INTERNAL_ERROR, "internal error", trace(), null));
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
