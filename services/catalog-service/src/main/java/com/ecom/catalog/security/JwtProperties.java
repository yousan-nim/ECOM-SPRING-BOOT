package com.ecom.catalog.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Type-safe binding for {@code app.jwt.*} in application.yml.
 *
 * <p>Wire it in {@code CatalogApplication} with {@code @EnableConfigurationProperties(JwtProperties.class)}.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256. " +
                    "Set the JWT_SECRET env var.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer is required");
        }
        if (accessTokenTtl == null  || accessTokenTtl.isNegative()  || accessTokenTtl.isZero()) {
            throw new IllegalStateException("app.jwt.access-token-ttl must be > 0");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalStateException("app.jwt.refresh-token-ttl must be > 0");
        }
    }
}
