package com.smartlockr.iam.application.service;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {
    @Mock
    private UserRepository userRepository;

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private UserService userService;

    private final UUID userId = UUID.randomUUID();
    @Mock
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper);
        given(jwt.getSubject()).willReturn(userId.toString());
    }

    @Test
    @DisplayName("Debe actualizar los campos del User para un CONSUMER y retornar UserResponse")
    void updateUserSettings_Consumer_Success() {
        // GIVEN
        UpdateUserSettings settings = new UpdateUserSettings(
                "Alex Dev",
                "https://avatar.io/alex",
                false,
                false,
                false);

        User existingUser = User.builder()
                .id(userId)
                .fullName("Nombre Antiguo")
                .avatarUrl("https://old.io")
                .role(Role.CONSUMER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        var result = userService.updateUserSettings(settings, jwt);

        // THEN
        then(userRepository).should().save(argThat(user -> {
            assertThat(user.getFullName()).isEqualTo(settings.fullName());
            assertThat(user.getAvatarUrl()).isEqualTo(settings.avatarUrl());
            return true;
        }));

        assertThat(result)
                .isNotNull()
                .extracting(UserResponse::fullName, UserResponse::avatarUrl)
                .containsExactly(settings.fullName(), settings.avatarUrl());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("CONSUMER: No debería actualizar por usar un nombre prohibido")
    void updateUserSettings_Consumer_Name_Constraint_Fails() {
        // GIVEN
        UpdateUserSettings settings = new UpdateUserSettings(
                "admin",
                "https://avatar.io/alex",
                false,
                false,
                false);

        User existingUser = User.builder()
                .id(userId)
                .fullName("Nombre Antiguo")
                .role(Role.CONSUMER)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));

        // WHEN & THEN
        assertThatThrownBy(() -> userService.updateUserSettings(settings, jwt))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved for system use");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("ADMIN: Debe permitir actualizar incluso con un nombre prohibido")
    void updateUserSettings_Admin_Success_With_Restricted_Name() {
        // GIVEN
        UpdateUserSettings settings = new UpdateUserSettings(
                "admin",
                "https://avatar.io/alex",
                false,
                false,
                false);

        User adminUser = User.builder()
                .id(userId)
                .fullName("Admin Name")
                .role(Role.ADMIN)
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(adminUser));
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        var result = userService.updateUserSettings(settings, jwt);

        // THEN
        assertThat(result.fullName()).isEqualTo("admin");
        verify(userRepository, times(1)).save(any(User.class));
    }
}