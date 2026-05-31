package com.ecom.catalog.security;

import com.ecom.catalog.domain.Role;
import com.ecom.catalog.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring-Security view of our domain {@link User}.
 *
 * <p>We keep this <strong>separate</strong> from the {@link User} entity so the domain
 * stays free of framework concerns. The principal stored in {@code SecurityContext}
 * exposes both the public id (used for audit / API responses) and the internal id
 * (used for DB look-ups inside services).</p>
 */
public final class AppUserPrincipal implements UserDetails {

    private final Long      id;
    private final UUID      publicId;
    private final String    email;
    private final String    passwordHash;
    private final boolean   enabled;
    private final Set<Role> roles;

    public AppUserPrincipal(User user, Set<Role> roles) {
        this.id           = user.getId();
        this.publicId     = user.getPublicId();
        this.email        = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled      = user.isActive();
        this.roles        = roles;
    }

    public Long       id()        { return id; }
    public UUID       publicId()  { return publicId; }
    public String     email()     { return email; }
    public Set<Role>  roles()     { return roles; }

    // ── UserDetails contract ─────────────────────────────────────────────
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.authority()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override public String  getPassword()              { return passwordHash; }
    @Override public String  getUsername()              { return email; }
    @Override public boolean isAccountNonExpired()      { return enabled; }
    @Override public boolean isAccountNonLocked()       { return enabled; }
    @Override public boolean isCredentialsNonExpired()  { return enabled; }
    @Override public boolean isEnabled()                { return enabled; }
}
