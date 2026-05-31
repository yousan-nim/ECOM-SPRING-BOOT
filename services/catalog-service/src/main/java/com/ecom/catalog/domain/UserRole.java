package com.ecom.catalog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Maps to {@code user_roles} table — a join row granting one role to one user.
 * Composite primary key (user_id, role) → {@link UserRoleId}.
 */
@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "granted_at", insertable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private Long grantedBy;

    public static UserRole grant(Long userId, Role role) {
        UserRole r = new UserRole();
        r.userId = userId;
        r.role = role;
        return r;
    }
}
