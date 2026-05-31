package com.ecom.catalog.web;

import com.ecom.catalog.repository.UserRepository;
import com.ecom.catalog.repository.UserRoleRepository;
import com.ecom.catalog.security.AppUserPrincipal;
import com.ecom.catalog.web.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile endpoints.")
public class UserController {

    private final UserRepository     users;
    private final UserRoleRepository roles;

    public UserController(UserRepository users, UserRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Operation(summary = "Current authenticated user")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        var user = users.findById(principal.id()).orElseThrow();
        return UserResponse.of(user, roles.findRolesByUserId(user.getId()));
    }
}
