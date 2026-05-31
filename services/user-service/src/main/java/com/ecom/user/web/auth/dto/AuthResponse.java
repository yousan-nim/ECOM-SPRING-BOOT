package com.ecom.user.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type")    String tokenType,    // always "Bearer"
        @JsonProperty("expires_in")    long   expiresIn,    // access-token TTL, seconds
        UserResponse user
) {
    public static AuthResponse of(String access, String refresh, long expiresIn, UserResponse u) {
        return new AuthResponse(access, refresh, "Bearer", expiresIn, u);
    }
}
