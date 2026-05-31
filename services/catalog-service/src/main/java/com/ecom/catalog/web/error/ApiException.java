package com.ecom.catalog.web.error;

import org.springframework.http.HttpStatus;

/**
 * Base for application-thrown exceptions. Carries everything needed to build a
 * RFC-7807 {@code ProblemDetail} response.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String     code;       // machine-readable, e.g. "EMAIL_TAKEN"
    private final String     title;      // short, fixed per code

    protected ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code   = code;
        this.title  = title;
    }

    public HttpStatus status() { return status; }
    public String     code()   { return code; }
    public String     title()  { return title; }
}
