package com.ecom.user.service;

import com.ecom.user.domain.User;
import com.ecom.user.repository.UserRepository;
import com.ecom.user.repository.UserRoleRepository;
import com.ecom.user.web.auth.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ecom.user.web.error.ValidationException;
import com.ecom.user.web.auth.dto.UpdateProfileRequest;

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

    /**
    * Partial update of the caller's own profile. Only non-null fields are applied;
    * an empty string clears the optional phone/avatarUrl. The managed entity is
    * flushed by dirty-checking — no explicit save needed.
    */
    @Transactional
        public UserResponse updateProfile(Long userId, UpdateProfileRequest req) { 
            User user = users.findById(userId).orElseThrow(); 

            if (req.fullName() != null) { 
                if (req.fullName().isBlank()) { 
                    throw new ValidationException("fullName must not be blank when present"); 
                }
                user.setFullName(req.fullName().trim());
            }

            if (req.phone() != null) { 
                user.setPhone(emptyToNull(req.phone()));
            }

            if (req.avatarUrl() != null) { 
                user.setAvatarUrl(emptyToNull(req.avatarUrl()));
            }

            return UserResponse.of(user, userRoles.findRolesByUserId(user.getId()));
        }

        private static String emptyToNull(String s) { 
            return (s == null || s.isBlank()) ? null : s.trim();
        }
}
