package com.ecom.user.service;

import com.ecom.user.domain.Role;
import com.ecom.user.domain.User;
import com.ecom.user.repository.UserRepository;
import com.ecom.user.repository.UserRoleRepository;
import com.ecom.user.web.auth.dto.UpdateProfileRequest;
import com.ecom.user.web.auth.dto.UserResponse;
import com.ecom.user.web.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository     users;
    @Mock private UserRoleRepository userRoles;

    @InjectMocks private UserService userService;

    /** A managed User as loaded from the DB, with pre-existing optional fields set. */
    private User existingUser() {
        User u = User.newCustomer("foo@bar.com", "hashed-pw", "Old Name", "0811111111");
        u.setId(1L);
        u.setPublicId(UUID.randomUUID());
        u.setAvatarUrl("https://cdn/old.png");
        return u;
    }

    @Test
    @DisplayName("updateProfile: trims and applies a new full_name")
    void updateProfile_setsFullName() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRoles.findRolesByUserId(1L)).thenReturn(Set.of(Role.CUSTOMER));

        UserResponse resp = userService.updateProfile(
                1L, new UpdateProfileRequest("  New Name  ", null, null));

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(resp.fullName()).isEqualTo("New Name");
        // untouched fields stay as they were
        assertThat(user.getPhone()).isEqualTo("0811111111");
        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn/old.png");
        assertThat(resp.roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    @DisplayName("updateProfile: empty-string phone clears the field to null")
    void updateProfile_emptyPhoneClears() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRoles.findRolesByUserId(1L)).thenReturn(Set.of(Role.CUSTOMER));

        userService.updateProfile(1L, new UpdateProfileRequest(null, "", null));

        assertThat(user.getPhone()).isNull();
    }

    @Test
    @DisplayName("updateProfile: non-blank phone is trimmed and applied")
    void updateProfile_setsPhone() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRoles.findRolesByUserId(1L)).thenReturn(Set.of(Role.CUSTOMER));

        userService.updateProfile(1L, new UpdateProfileRequest(null, "  +66811112222  ", null));

        assertThat(user.getPhone()).isEqualTo("+66811112222");
    }

    @Test
    @DisplayName("updateProfile: empty-string avatar_url clears the field to null")
    void updateProfile_emptyAvatarClears() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRoles.findRolesByUserId(1L)).thenReturn(Set.of(Role.CUSTOMER));

        userService.updateProfile(1L, new UpdateProfileRequest(null, null, ""));

        assertThat(user.getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("updateProfile: blank (non-null) full_name → ValidationException, nothing changes")
    void updateProfile_blankFullName_throws() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                userService.updateProfile(1L, new UpdateProfileRequest("   ", "0822222222", null)))
                .isInstanceOf(ValidationException.class);

        // fields left untouched — the blank check fires before any mutation of phone
        assertThat(user.getFullName()).isEqualTo("Old Name");
        assertThat(user.getPhone()).isEqualTo("0811111111");
    }

    @Test
    @DisplayName("updateProfile: all-null request leaves every field unchanged")
    void updateProfile_allNull_noChange() {
        User user = existingUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRoles.findRolesByUserId(1L)).thenReturn(Set.of(Role.CUSTOMER));

        userService.updateProfile(1L, new UpdateProfileRequest(null, null, null));

        assertThat(user.getFullName()).isEqualTo("Old Name");
        assertThat(user.getPhone()).isEqualTo("0811111111");
        assertThat(user.getAvatarUrl()).isEqualTo("https://cdn/old.png");
    }

    @Test
    @DisplayName("updateProfile: unknown user id → NoSuchElementException, no role lookup")
    void updateProfile_unknownUser_throws() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.updateProfile(99L, new UpdateProfileRequest("New Name", null, null)))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(userRoles, never()).findRolesByUserId(anyLong());
    }
}
