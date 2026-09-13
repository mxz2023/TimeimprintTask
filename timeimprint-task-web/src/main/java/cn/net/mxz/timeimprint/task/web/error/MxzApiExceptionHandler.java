package cn.net.mxz.timeimprint.task.web.error;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiMessages;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponses;
import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class MxzApiExceptionHandler {

    @ExceptionHandler(MxzApplicationException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleApp(MxzApplicationException ex) {
        HttpStatus status = switch (ex.errorCode()) {
            case "RESOURCE_NOT_FOUND", "EXTENSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "RETRY_LATER" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "REQUEST_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "UNSUPPORTED_MEDIA_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case "IDEMPOTENCY_CONFLICT", "REVISION_CONFLICT", "STATE_CONFLICT", "COMMAND_NOT_SUPPORTED" ->
                HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new MxzApiResponse<>(
                        ex.errorCode(),
                        MxzApiMessages.error(ex.errorCode(), ex.getMessage()),
                        MxzApiResponses.traceId(),
                        null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(error(MxzApiErrorCodes.INVALID_REQUEST, "validation failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(error(MxzApiErrorCodes.INVALID_REQUEST, "invalid json"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleMedia(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error(MxzApiErrorCodes.UNSUPPORTED_MEDIA_TYPE, "unsupported media type"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.badRequest()
                .body(error(MxzApiErrorCodes.INVALID_REQUEST, "method not allowed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<MxzApiResponse<Void>> handleMissing(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(MxzApiErrorCodes.RESOURCE_NOT_FOUND, "not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MxzApiResponse<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(MxzApiErrorCodes.INTERNAL_ERROR, "internal error"));
    }

    private static MxzApiResponse<Void> error(String code, String detail) {
        return new MxzApiResponse<>(code, MxzApiMessages.error(code, detail), MxzApiResponses.traceId(), null);
    }
}
