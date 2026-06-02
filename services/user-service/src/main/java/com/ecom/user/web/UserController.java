package com.ecom.user.web;

import com.ecom.user.security.AppUserPrincipal;
import com.ecom.user.service.UserService;
import com.ecom.user.web.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ecom.user.web.auth.dto.UpdateProfileRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile endpoints.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Current authenticated user")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.getProfile(principal.id());
    }

    @Operation(summary = "Update current user's profile")
    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody UpdateProfileRequest req) { 
        return userService.updateProfile(principal.id(), req);
    }
}