package com.ecom.catalog.web.auth.dto;

import com.ecom.catalog.domain.Role;
import com.ecom.catalog.domain.User;
import com.ecom.catalog.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;

/** Public user representation — never includes password hash or internal id. */
public record UserResponse(
        UUID id,
        String email,
        @JsonProperty("full_name")      String fullName,
        String phone,
        @JsonProperty("email_verified") boolean emailVerified,
        UserStatus status,
        Set<Role> roles
) {
    public static UserResponse of(User u, Set<Role> roles) {
        return new UserResponse(
                u.getPublicId(),
                u.getEmail(),
                u.getFullName(),
                u.getPhone(),
                u.isEmailVerified(),
                u.getStatus(),
                roles
        );
    }
}
