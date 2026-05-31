package com.ecom.catalog.service;

import com.ecom.catalog.domain.RefreshToken;
import com.ecom.catalog.domain.Role;
import com.ecom.catalog.domain.User;
import com.ecom.catalog.domain.UserRole;
import com.ecom.catalog.repository.RefreshTokenRepository;
import com.ecom.catalog.repository.UserRepository;
import com.ecom.catalog.repository.UserRoleRepository;
import com.ecom.catalog.security.JwtService;
import com.ecom.catalog.security.TokenHasher;
import com.ecom.catalog.web.auth.dto.*;
import com.ecom.catalog.web.error.AccountSuspendedException;
import com.ecom.catalog.web.error.EmailAlreadyExistsException;
import com.ecom.catalog.web.error.InvalidCredentialsException;
import com.ecom.catalog.web.error.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Owns the auth use-cases: register, login, refresh, logout.
 *
 * <p>Refresh-token strategy: single-use rotation with reuse detection (see
 * {@link #refresh}). Raw refresh tokens never touch the database — we store
 * SHA-256 hashes.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository         users;
    private final UserRoleRepository     userRoles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwt;
    private final TokenHasher            tokenHasher;

    public AuthService(UserRepository users,
                       UserRoleRepository userRoles,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwt,
                       TokenHasher tokenHasher) {
        this.users           = users;
        this.userRoles       = userRoles;
        this.refreshTokens   = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt             = jwt;
        this.tokenHasher     = tokenHasher;
    }

    // ─── Register ────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest http) {
        String email = req.email().toLowerCase();

        if (users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.newCustomer(
                email,
                passwordEncoder.encode(req.password()),
                req.fullName(),
                emptyToNull(req.phone()));
        user = users.saveAndFlush(user);   // flush ⇒ DB assigns id + public_id

        userRoles.save(UserRole.grant(user.getId(), Role.CUSTOMER));
        Set<Role> roles = Set.of(Role.CUSTOMER);

        log.info("registered new user id={} email={}", user.getId(), user.getEmail());
        return issueTokens(user, roles, http);
    }

    // ─── Login ───────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest http) {
        User user = users.findByEmail(req.email().toLowerCase()).orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }

        Set<Role> roles = userRoles.findRolesByUserId(user.getId());
        user.setLastLoginAt(Instant.now());

        return issueTokens(user, roles, http);
    }

    // ─── Refresh (single-use rotation + reuse detection) ─────────────────
    @Transactional
    public AuthResponse refresh(String rawToken, HttpServletRequest http) {
        String hash = tokenHasher.hash(rawToken);

        RefreshToken row = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Token not found"));

        // 🚨 REUSE DETECTED — token already revoked.
        if (row.isRevoked()) {
            int affected = refreshTokens.revokeAllForUser(row.getUserId(),
                    Instant.now(), "REUSE_DETECTED");
            log.warn("refresh-token reuse detected userId={} affected={}", row.getUserId(), affected);
            throw new InvalidRefreshTokenException("Token reuse detected — all sessions revoked");
        }

        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Token expired");
        }

        User user = users.findById(row.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("User not found"));
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }

        // Rotate: revoke this row, issue a new one.
        row.revoke("ROTATED");
        Set<Role> roles = userRoles.findRolesByUserId(user.getId());

        AuthResponse response = issueTokens(user, roles, http);

        // Link the chain (replaced_by points to the newly created row).
        refreshTokens.findByTokenHash(tokenHasher.hash(response.refreshToken()))
                .ifPresent(newRow -> row.setReplacedBy(newRow.getId()));

        return response;
    }

    // ─── Logout (revoke this refresh token) ──────────────────────────────
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = tokenHasher.hash(rawToken);
        refreshTokens.findByTokenHash(hash).ifPresent(t -> t.revoke("LOGOUT"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────
    private AuthResponse issueTokens(User user, Set<Role> roles, HttpServletRequest http) {
        String access = jwt.generateAccessToken(user, roles);

        String rawRefresh = tokenHasher.generateRawToken();
        String hash       = tokenHasher.hash(rawRefresh);
        Instant expires   = Instant.now().plus(jwt.refreshTokenDuration());

        refreshTokens.save(RefreshToken.issue(
                user.getId(), hash, expires,
                trimToLen(http.getHeader("User-Agent"), 500),
                clientIp(http)));

        return AuthResponse.of(access, rawRefresh, jwt.accessTtlSeconds(),
                UserResponse.of(user, roles));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String trimToLen(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
