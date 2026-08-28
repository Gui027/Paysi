package com.paysi.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.core.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Tradução única de erro de domínio/formato para o envelope HTTP do documento 2, §4.1.
 * Cross-cutting a todos os módulos web — não é específico de identity.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), "INVALID", fe.getDefaultMessage()))
                .toList();
        var body = ApiError.of("VALIDATION_ERROR", "Um ou mais campos são inválidos", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> handleValidation(ValidationException ex) {
        var body = ApiError.of(ex.code(), ex.getMessage(), ex.field());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        var body = ApiError.of(ex.code(), ex.getMessage(), ex.field());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.code(), ex.getMessage(), (String) null));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ex.code(), ex.getMessage(), (String) null));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ex.code(), ex.getMessage(), (String) null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        InvalidFormatException invalidFormat = findCause(ex, InvalidFormatException.class);
        if (invalidFormat != null && !invalidFormat.getPath().isEmpty()) {
            String field = invalidFormat.getPath().getLast().getFieldName();
            return ResponseEntity.badRequest().body(ApiError.of(
                    "INVALID_ENUM", "Valor inválido para o campo " + field, field));
        }
        return ResponseEntity.badRequest().body(ApiError.of(
                "MALFORMED_JSON", "Corpo da requisição é inválido", (String) null));
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return type.cast(cause);
        }
        return null;
    }
}
