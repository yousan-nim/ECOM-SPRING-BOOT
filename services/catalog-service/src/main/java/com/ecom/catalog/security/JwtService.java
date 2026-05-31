package com.ecom.catalog.security;

import com.ecom.catalog.domain.Role;
import com.ecom.catalog.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates + parses HS256 access tokens (JWT).
 *
 * <p>Claims layout:</p>
 * <pre>
 *  {
 *    "iss":   "ecom-catalog",
 *    "sub":   "&lt;user.publicId UUID&gt;",
 *    "iat":   epoch seconds,
 *    "exp":   epoch seconds,
 *    "email": "...",
 *    "roles": ["CUSTOMER", "VENDOR_ADMIN"]
 *  }
 * </pre>
 *
 * <p>Security notes:</p>
 * <ul>
 *   <li>Algorithm is hard-coded HS256 — never trust the {@code alg} header on parse.</li>
 *   <li>Subject is the user's <em>publicId</em>, never the internal BIGSERIAL.</li>
 *   <li>Issuer is verified on parse — rejects tokens from other systems.</li>
 * </ul>
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user, Set<Role> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());

        List<String> roleStrings = roles.stream().map(Enum::name).toList();

        return Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getPublicId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("email", user.getEmail())
                .claim("roles", roleStrings)
                .signWith(signingKey, Jwts.SIG.HS256)   // 🔒 explicit algorithm
                .compact();
    }

    /** Parse + verify. Throws {@link JwtException} on signature / expiry / issuer / alg failure. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)              // 🔒 enforce HS256, reject "none"
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTtlSeconds() {
        return props.accessTokenTtl().getSeconds();
    }

    public Duration refreshTokenDuration() {
        return props.refreshTokenTtl();
    }

    public static UUID extractSubject(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public static Set<Role> extractRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(Role::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
