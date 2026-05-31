package com.ecom.catalog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import org.hibernate.annotations.ColumnTransformer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Refresh token stored as SHA-256 hash (never raw).
 * Rotation: once redeemed, {@code revokedAt} is set and {@code replacedBy} points to the new row.
 * Reuse of a revoked token is a security incident → revoke the entire family.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", insertable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "user_agent")
    private String userAgent;

    /** PG {@code inet} isn't implicitly cast from varchar — cast the bind param on write. */
    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    @Column(name = "replaced_by")
    private Long replacedBy;

    // ─── Factories / helpers ─────────────────────────────────────────────
    public static RefreshToken issue(Long userId, String tokenHash, Instant expiresAt,
                                     String userAgent, String ipAddress) {
        RefreshToken t = new RefreshToken();
        t.userId = userId;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.userAgent = userAgent;
        t.ipAddress = ipAddress;
        return t;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(String reason) {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = reason;
        }
    }
}
