package com.ecom.user.domain;

/**
 * Application-level roles. Stored as TEXT in user_roles.role (matches DB CHECK constraint).
 *
 * <p>Spring Security adds the "ROLE_" prefix automatically when checking with
 * {@code hasRole("CUSTOMER")} — so authorities are stored as e.g. "ROLE_CUSTOMER".</p>
 */
public enum Role {
    CUSTOMER,
    VENDOR_ADMIN,
    PLATFORM_ADMIN,
    PLATFORM_SUPPORT;

    /** Returns the Spring Security authority string (e.g. "ROLE_CUSTOMER"). */
    public String authority() {
        return "ROLE_" + name();
    }
}
