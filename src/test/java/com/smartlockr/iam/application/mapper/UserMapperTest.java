package com.smartlockr.iam.application.mapper;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = Mappers.getMapper(UserMapper.class);
    }

    @Test
    @DisplayName("updateExistingUser - copia fullName, avatarUrl, hasSeenWelcome y preferencias")
    void shouldUpdateExistingUserFields() {
        var settings = new UpdateUserSettings("John Doe", "https://avatar.io/x.png", true, true, false);
        var user = User.builder()
                .fullName("Old Name")
                .avatarUrl("https://old.io")
                .hasSeenWelcome(false)
                .role(Role.CONSUMER)
                .userPreferences(new UserPreferences(false, true))
                .build();

        userMapper.updateExistingUser(settings, user);

        assertThat(user.getFullName()).isEqualTo("John Doe");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.io/x.png");
        assertThat(user.isHasSeenWelcome()).isTrue();
        assertThat(user.getUserPreferences().isReceiveReceipts()).isTrue();
        assertThat(user.getUserPreferences().isReceivesPromotions()).isFalse();
    }

    @Test
    @DisplayName("updateExistingUser - ignora campos null de UpdateUserSettings")
    void shouldIgnoreNullFieldsInUpdate() {
        var settings = new UpdateUserSettings(null, null, false, false, false);
        var user = User.builder()
                .fullName("Keep Name")
                .avatarUrl("keep-avatar")
                .hasSeenWelcome(true)
                .role(Role.CONSUMER)
                .userPreferences(new UserPreferences(true, true))
                .build();

        userMapper.updateExistingUser(settings, user);

        assertThat(user.getFullName()).isEqualTo("Keep Name");
        assertThat(user.getAvatarUrl()).isEqualTo("keep-avatar");
        assertThat(user.isHasSeenWelcome()).isFalse();
    }

    @Test
    @DisplayName("toUserResponse - copia campos del User a UserResponse")
    void shouldMapUserToUserResponse() {
        var prefs = new UserPreferences(true, false);
        var user = User.builder()
                .id(com.smartlockr.shared.utils.UuidV7.generate())
                .fullName("Jane Doe")
                .email("jane@test.local")
                .avatarUrl("avatar.png")
                .hasSeenWelcome(true)
                .suspended(false)
                .role(Role.CONSUMER)
                .userPreferences(prefs)
                .build();

        UserResponse response = userMapper.toUserResponse(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.email()).isEqualTo("jane@test.local");
        assertThat(response.avatarUrl()).isEqualTo("avatar.png");
        assertThat(response.role()).isEqualTo(Role.CONSUMER);
        assertThat(response.receiveReceipts()).isTrue();
        assertThat(response.receivesPromotions()).isFalse();
    }

    @Test
    @DisplayName("toNewUser - mapea email/fullName/avatarUrl y aplica defaults CONSUMER")
    void shouldMapOidcUserToNewUserWithDefaults() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn("new@test.local");
        when(oidcUser.getFullName()).thenReturn("New User");
        when(oidcUser.getPicture()).thenReturn("https://avatar.io/new.png");

        User user = userMapper.toNewUser(oidcUser);

        assertThat(user.getEmail()).isEqualTo("new@test.local");
        assertThat(user.getFullName()).isEqualTo("New User");
        assertThat(user.getAvatarUrl()).isEqualTo("https://avatar.io/new.png");
        assertThat(user.getRole()).isEqualTo(Role.CONSUMER);
        assertThat(user.isHasSeenWelcome()).isFalse();
        assertThat(user.isSuspended()).isFalse();
        assertThat(user.getUserPreferences()).isNotNull();
        assertThat(user.getUserPreferences().isReceiveReceipts()).isTrue();
        assertThat(user.getUserPreferences().isReceivesPromotions()).isTrue();
    }
}
