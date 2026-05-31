package com.ecom.user.web.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email(message = "must be a well-formed email address")
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 100, message = "size must be between 8 and 100")
        String password,

        @NotBlank
        @Size(max = 255)
        String fullName,

        @Pattern(regexp = "^\\+?[0-9 \\-]{6,20}$|^$", message = "invalid phone format")
        String phone
) {}
