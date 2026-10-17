package az.yolcu.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        var msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "Yanlış sorğu";
        return status(HttpStatus.BAD_REQUEST, "VALIDATION", msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> badRequest(Exception e) {
        return status(HttpStatus.BAD_REQUEST, "VALIDATION", "Yanlış sorğu");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return status(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tapılmadı");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return status(HttpStatus.METHOD_NOT_ALLOWED, "VALIDATION", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return status(HttpStatus.FORBIDDEN, "FORBIDDEN", "İcazə yoxdur");
    }

    // client closed an SSE stream: nothing can be written back, EventBus already logged the removal
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void clientGone() {}

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        log.error("Unhandled error", e);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Daxili xəta");
    }

    private static ResponseEntity<ApiError> status(HttpStatus s, String code, String msg) {
        return ResponseEntity.status(s).body(ApiError.of(code, msg));
    }
}
