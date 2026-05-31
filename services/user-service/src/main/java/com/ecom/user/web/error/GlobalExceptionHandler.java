package com.ecom.user.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal global error handler.
 *
 * <p>All responses use RFC-7807 {@code application/problem+json} format with these
 * extensions: {@code code}, {@code timestamp}, {@code trace_id}, and (for validation)
 * {@code errors}.</p>
 *
 * <p>This will eventually move to {@code libs/common-web} and be shared by all services.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERR_TYPE_BASE = "https://api.ecom.dev/errors/";

    // ── Domain exceptions (our own) ──────────────────────────────────────
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest req) {
        return base(ex.status(), ex.title(), ex.getMessage(), ex.code(), req);
    }

    // ── Bean Validation on @RequestBody ──────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, Object>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("field", fe.getField());
                    m.put("code", fe.getCode());
                    m.put("message", fe.getDefaultMessage());
                    if (fe.getRejectedValue() != null) {
                        m.put("rejected_value", fe.getRejectedValue().toString());
                    }
                    return m;
                })
                .toList();

        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "VALIDATION_FAILED", req);
        pd.setProperty("errors", fields);
        return pd;
    }

    // ── Bean Validation on @RequestParam / @PathVariable ─────────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Validation failed",
                ex.getMessage(), "VALIDATION_FAILED", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Malformed request body",
                "Request body could not be parsed", "MALFORMED_BODY", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return base(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Parameter '" + ex.getName() + "' has an invalid value",
                "INVALID_PARAMETER", req);
    }

    // ── Spring Security ──────────────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCreds(BadCredentialsException ex, HttpServletRequest req) {
        // Don't include details (avoid info leak / enumeration).
        return base(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid email or password", "INVALID_CREDENTIALS", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return base(HttpStatus.FORBIDDEN, "Access denied",
                "You do not have permission to access this resource",
                "ACCESS_DENIED", req);
    }

    // ── Fallback ─────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAll(Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString();
        log.error("unhandled exception [traceId={}]", traceId, ex);

        ProblemDetail pd = base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please contact support with the trace ID.",
                "INTERNAL_ERROR", req);
        pd.setProperty("trace_id", traceId);
        return pd;
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private ProblemDetail base(HttpStatus status, String title, String detail,
                               String code, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(ERR_TYPE_BASE + code.toLowerCase().replace('_', '-')));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
