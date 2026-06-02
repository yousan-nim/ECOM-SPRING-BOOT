package com.ecom.user.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of the caller's own profile (PATCH semantics).
 * null = leave unchanged; empty string clears phone/avatar_url.
 * Email is not updatable here (needs a separate verification flow).
 */
public record UpdateProfileRequest(
    @JsonProperty("full_name")
    @Size(max = 100, message = "full_name must be at most 100 characters")
    String fullName,

    @Pattern(regexp = "^$|^\\+?[1-9]\\d{1,14}$", message = "phone must be a valid E.164 number or empty")
    String phone,

    @JsonProperty("avatar_url")
    @Size(max = 500, message = "avatar_url must be at most 500 characters")
    String avatarUrl
) {}