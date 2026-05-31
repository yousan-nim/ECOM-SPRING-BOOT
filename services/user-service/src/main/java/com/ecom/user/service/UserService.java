package com.ecom.user.service;

import com.ecom.user.domain.User;
import com.ecom.user.repository.UserRepository;
import com.ecom.user.repository.UserRoleRepository;
import com.ecom.user.web.auth.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile read operations. Keeps user look-ups + {@link UserResponse}
 * mapping out of the controllers (controller → service → repository).
 */
@Service
public class UserService {

    private final UserRepository     users;
    private final UserRoleRepository userRoles;

    public UserService(UserRepository users, UserRoleRepository userRoles) {
        this.users     = users;
        this.userRoles = userRoles;
    }

    /** Profile of the user with the given internal id, including granted roles. */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = users.findById(userId).orElseThrow();
        return UserResponse.of(user, userRoles.findRolesByUserId(user.getId()));
    }
}
