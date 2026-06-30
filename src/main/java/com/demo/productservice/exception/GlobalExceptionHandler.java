package com.demo.productservice.exception;

import com.demo.productservice.dto.ApiResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Validation errors (400) ──────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Validation failed: " + errors));
    }

    // ── Not found (404) ──────────────────────────────────────────────────────
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Duplicate SKU (409) ──────────────────────────────────────────────────
    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateSkuException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Rate limiter (429) ───────────────────────────────────────────────────
    // This fires when >150 QPS hits the t3.micro — instead of OOM, clients get 429
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RequestNotPermitted ex) {
        log.warn("Rate limit exceeded — returning 429 to caller");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(ApiResponse.error(
                    "Too many requests. Server capacity is ~150 QPS on t3.micro. " +
                    "Please retry after 1 second."));
    }

    // ── Catch-all (500) ──────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error. Check server logs."));
    }
}
