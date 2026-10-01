package shirlin.ai.trigger.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import shirlin.ai.api.response.Response;
import shirlin.ai.types.exception.AppException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Response<Void>> security(SecurityException e) {
        return error(HttpStatus.UNAUTHORIZED, "AUTH_FAILED", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, AppException.class})
    public ResponseEntity<Response<Void>> badRequest(RuntimeException e) {
        String code = e instanceof AppException app ? app.getCode() : "INVALID_REQUEST";
        String message = e instanceof AppException app && app.getInfo() != null ? app.getInfo() : e.getMessage();
        return error(HttpStatus.BAD_REQUEST, code, message);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Response<Void>> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    private ResponseEntity<Response<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Response.<Void>builder()
                .code(code).info(message == null ? status.getReasonPhrase() : message).build());
    }
}
