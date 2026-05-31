package com.ecom.catalog.web.error;

import org.springframework.http.HttpStatus;

public class AccountSuspendedException extends ApiException {
    public AccountSuspendedException() {
        super(HttpStatus.FORBIDDEN,
              "ACCOUNT_SUSPENDED",
              "Account suspended",
              "This account has been suspended. Contact support.");
    }
}
