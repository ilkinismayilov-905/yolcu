package az.yolcu.common;

import org.springframework.http.HttpStatus;

/** Thrown by services; GlobalExceptionHandler turns it into { error: { code, message } }. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message); }
    public static ApiException validation(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", message); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    public static ApiException coinsInsufficient(String message) { return new ApiException(HttpStatus.CONFLICT, "COINS_INSUFFICIENT", message); }
    public static ApiException alreadyDone(String message) { return new ApiException(HttpStatus.CONFLICT, "ALREADY_DONE", message); }
    public static ApiException rateLimit(String message) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", message); }
}
