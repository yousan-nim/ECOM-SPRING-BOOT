package com.ecom.user.web.error;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException(String reason) {
        super(HttpStatus.UNAUTHORIZED,
              "INVALID_REFRESH_TOKEN",
              "Invalid refresh token",
              reason);
    }
}
