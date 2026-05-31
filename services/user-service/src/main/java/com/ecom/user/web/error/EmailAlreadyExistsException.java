package com.ecom.user.web.error;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT,
              "EMAIL_TAKEN",
              "Email already registered",
              "An account with email '" + email + "' already exists");
    }
}
