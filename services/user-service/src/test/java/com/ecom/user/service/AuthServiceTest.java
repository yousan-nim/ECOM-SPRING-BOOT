package com.ecom.user.service;

import com.ecom.user.domain.Role;
import com.ecom.user.domain.User;
import com.ecom.user.domain.UserRole;
import com.ecom.user.repository.RefreshTokenRepository;
import com.ecom.user.repository.UserRepository;
import com.ecom.user.repository.UserRoleRepository;
import com.ecom.user.security.JwtService;
import com.ecom.user.security.TokenHasher;
import com.ecom.user.web.auth.dto.AuthResponse;
import com.ecom.user.web.auth.dto.ChangePasswordRequest;
import com.ecom.user.web.auth.dto.RegisterRequest;
import com.ecom.user.web.error.EmailAlreadyExistsException;
import com.ecom.user.web.error.InvalidCredentialsException;
import com.ecom.user.web.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository         users;
    @Mock private UserRoleRepository     userRoles;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtService             jwt;
    @Mock private TokenHasher            tokenHasher;
    @Mock private HttpServletRequest     http;

    @InjectMocks private AuthService authService;

    private RegisterRequest request(String email) {
        return new RegisterRequest(email, "password123", "Foo Bar", "");
    }

    /** A User as it looks AFTER saveAndFlush — DB has assigned id + public_id. */
    private User persistedUser(String email) {
        User u = User.newCustomer(email, "hashed-pw", "Foo Bar", null);
        u.setId(1L);
        u.setPublicId(UUID.randomUUID());
        return u;
    }

    /** Stub the token-issuing collaborators reached on the happy path. */
    private void stubTokenIssuing() {
        when(jwt.generateAccessToken(any(), any())).thenReturn("access-jwt");
        when(jwt.refreshTokenDuration()).thenReturn(Duration.ofDays(30));
        when(jwt.accessTtlSeconds()).thenReturn(900L);
        when(tokenHasher.generateRawToken()).thenReturn("raw-refresh");
        when(tokenHasher.hash("raw-refresh")).thenReturn("hashed-refresh");
        when(http.getHeader("User-Agent")).thenReturn("JUnit");
        when(http.getHeader("X-Forwarded-For")).thenReturn(null);
        when(http.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("register: new email → persists user, grants CUSTOMER role, returns token pair")
    void register_withNewEmail_succeeds() {
        when(users.existsByEmail("foo@bar.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(users.saveAndFlush(any(User.class))).thenReturn(persistedUser("foo@bar.com"));
        stubTokenIssuing();

        AuthResponse resp = authService.register(request("foo@bar.com"), http);

        assertThat(resp.accessToken()).isEqualTo("access-jwt");
        assertThat(resp.refreshToken()).isEqualTo("raw-refresh");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.expiresIn()).isEqualTo(900L);
        assertThat(resp.user().email()).isEqualTo("foo@bar.com");
        assertThat(resp.user().roles()).containsExactly(Role.CUSTOMER);

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoles).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);

        verify(refreshTokens).save(any());
    }

    @Test
    @DisplayName("register: stores the BCrypt hash, never the raw password")
    void register_hashesPassword() {
        when(users.existsByEmail("foo@bar.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(users.saveAndFlush(any(User.class))).thenReturn(persistedUser("foo@bar.com"));
        stubTokenIssuing();

        authService.register(request("foo@bar.com"), http);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    @DisplayName("register: normalizes email to lowercase before the uniqueness check")
    void register_normalizesEmail() {
        when(users.existsByEmail("foo@bar.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(users.saveAndFlush(any(User.class))).thenReturn(persistedUser("foo@bar.com"));
        stubTokenIssuing();

        authService.register(request("Foo@Bar.COM"), http);

        verify(users).existsByEmail("foo@bar.com");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("foo@bar.com");
    }

    @Test
    @DisplayName("register: duplicate email → throws EmailAlreadyExistsException, persists nothing")
    void register_whenEmailExists_throws() {
        when(users.existsByEmail("foo@bar.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("foo@bar.com"), http))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(users, never()).saveAndFlush(any());
        verify(userRoles, never()).save(any());
        verify(refreshTokens, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    // ─── changePassword ──────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword: correct current + new differs → re-encodes hash, revokes all sessions")
    void changePassword_succeeds() {
        User user = persistedUser("foo@bar.com");           // passwordHash = "hashed-pw"
        when(users.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("oldpass", "hashed-pw")).thenReturn(true);
        when(passwordEncoder.matches("newpass123", "hashed-pw")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");

        authService.changePassword(1L, new ChangePasswordRequest("oldpass", "newpass123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokens).revokeAllForUser(eq(1L), any(Instant.class), eq("PASSWORD_CHANGED"));
    }

    @Test
    @DisplayName("changePassword: wrong current password → ValidationException, nothing changes")
    void changePassword_wrongCurrent_throws() {
        User user = persistedUser("foo@bar.com");
        when(users.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.changePassword(1L, new ChangePasswordRequest("wrongpass", "newpass123")))
                .isInstanceOf(ValidationException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hashed-pw");
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokens, never()).revokeAllForUser(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("changePassword: new password equals current → ValidationException, nothing changes")
    void changePassword_sameAsCurrent_throws() {
        User user = persistedUser("foo@bar.com");
        when(users.findById(1L)).thenReturn(java.util.Optional.of(user));
        // both the "current" check and the "differs" check call matches("oldpass", hash) → true
        when(passwordEncoder.matches("oldpass", "hashed-pw")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.changePassword(1L, new ChangePasswordRequest("oldpass", "oldpass")))
                .isInstanceOf(ValidationException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hashed-pw");
        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokens, never()).revokeAllForUser(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("changePassword: unknown user id → InvalidCredentialsException, no revoke")
    void changePassword_unknownUser_throws() {
        when(users.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() ->
                authService.changePassword(99L, new ChangePasswordRequest("oldpass", "newpass123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokens, never()).revokeAllForUser(anyLong(), any(), anyString());
    }
}
