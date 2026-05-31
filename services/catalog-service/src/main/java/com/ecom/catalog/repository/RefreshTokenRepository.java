package com.ecom.catalog.repository;

import com.ecom.catalog.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke the entire chain (family) reachable via {@code replaced_by} starting from
     * the given token id. Used when reuse-detected ⇒ assume the family is compromised.
     */
    @Modifying
    @Query(value = """
            WITH RECURSIVE family AS (
                SELECT id FROM refresh_tokens WHERE id = :startId
                UNION
                SELECT t.id FROM refresh_tokens t
                JOIN family f ON t.id = f.replaced_by
            )
            UPDATE refresh_tokens
               SET revoked_at = NOW(),
                   revoked_reason = COALESCE(revoked_reason, :reason)
             WHERE id IN (SELECT id FROM family)
               AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeFamily(@Param("startId") Long startId, @Param("reason") String reason);

    /**
     * Revoke the chain that starts from the *root* of the given token (walk backwards
     * by following replaced_by relationships in reverse). Simpler approximation: just
     * revoke every active token belonging to the same user.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);
}
