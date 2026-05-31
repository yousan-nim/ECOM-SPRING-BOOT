package com.ecom.catalog.security;

import com.ecom.catalog.domain.User;
import com.ecom.catalog.repository.UserRepository;
import com.ecom.catalog.repository.UserRoleRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user by email for Spring Security's {@code DaoAuthenticationProvider}.
 * Used during form-style {@code AuthenticationManager.authenticate(...)} (login flow).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository     users;
    private final UserRoleRepository roles;

    public AppUserDetailsService(UserRepository users, UserRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = users.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No user for email"));
        return new AppUserPrincipal(u, roles.findRolesByUserId(u.getId()));
    }
}
