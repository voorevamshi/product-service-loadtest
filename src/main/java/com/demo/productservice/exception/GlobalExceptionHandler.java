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
import java.util.concurrent.atomic.AtomicLong;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Running count of 429s — visible in LOAD METRICS summary log
    private static final AtomicLong rateLimitCount = new AtomicLong(0);
    private static final AtomicLong serverErrorCount = new AtomicLong(0);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error ->
                errors.put(((FieldError) error).getField(), error.getDefaultMessage()));
        log.warn("[400] Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed: " + errors));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ProductNotFoundException ex) {
        log.debug("[404] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateSkuException ex) {
        log.warn("[409] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Rate limiter fires here when >150 QPS hits the app.
     *
     * Log output you will see during k6 overload test:
     *   WARN  [429] #1240 Rate limit exceeded — productApi has no permits available
     *   WARN  [429] #1241 Rate limit exceeded — ...
     *
     * The #N counter shows cumulative 429s — use it to gauge how many requests
     * are being shed. At 200 QPS with limit=150, expect ~50 per second.
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RequestNotPermitted ex) {
        long count = rateLimitCount.incrementAndGet();
        // Log every 429 at WARN, but also log a milestone every 100 to avoid log spam
        if (count % 100 == 0) {
            log.warn("[429] #{} Rate limit milestone — {} requests shed so far this session. " +
                     "Limiter=productApi  capacity=150/s", count, count);
        } else {
            log.warn("[429] #{} Rate limit exceeded — request rejected by resilience4j productApi", count);
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
                .body(ApiResponse.error(
                    "Rate limit exceeded. Server is at capacity (150 req/s). Retry after 1s."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        long count = serverErrorCount.incrementAndGet();
        log.error("[500] #{} Unhandled exception: {}  (type={})",
                count, ex.getMessage(), ex.getClass().getSimpleName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error. See server logs for details."));
    }
}
