package com.ecom.user.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * SHA-256 hashing for refresh tokens. We can't use BCrypt for tokens because:
 *  - Lookup must be fast (BCrypt is intentionally slow ~250ms)
 *  - Tokens are already high-entropy (32 random bytes) — no salt needed
 *
 * Raw token format: 256-bit random, Base64URL-encoded (43 chars).
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** Generate a new random refresh-token string (43 chars, URL-safe). */
    public String generateRawToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return URL_ENCODER.encodeToString(buf);
    }

    /** Hash a raw token for DB storage / lookup. */
    public String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
