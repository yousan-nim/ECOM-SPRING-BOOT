package com.ecom.catalog.web.error;

import org.springframework.http.HttpStatus;

/**
 * Always use the same message whether the email is unknown or the password is wrong —
 * prevents user-enumeration attacks.
 */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED,
              "INVALID_CREDENTIALS",
              "Authentication failed",
              "Invalid email or password");
    }
}
