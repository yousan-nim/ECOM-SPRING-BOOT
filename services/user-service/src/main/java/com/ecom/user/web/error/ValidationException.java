package com.ecom.user.web.error;

import org.springframework.http.HttpStatus;


/**
 * Service-level validation failure (400). Use for rules that can't be expressed
 * with Bean Validation on the request body — e.g. "full_name must not be blank
 * when present" or "new password must differ from the current one".
 */
public class ValidationException extends ApiException {
    public ValidationException(String message) { 
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", message);
    }
}
