package com.smartlockr.iam.application.service;

import com.smartlockr.iam.application.dto.UpdateUserSettings;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import com.smartlockr.shared.utils.UrlConstraints;
import com.smartlockr.shared.utils.UserConstraints;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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

    UUID userId = UUID.randomUUID();
    Jwt jwt;
    UpdateUserSettings settings;
    User existingUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper);
        jwt = mock(Jwt.class);
        existingUser = User.builder()
                .id(userId)
                .fullName("Nombre Antiguo")
                .avatarUrl("https://old.io")
                .build();
    }

    @Test
    @DisplayName("Debe actualizar los campos del User y retornar UserResponse")
    void updateUserSettings_Success() {

        settings = new UpdateUserSettings(
                "Alex Dev",
                "https://avatar.io/alex",
                false,
                false,
                false);
        // GIVEN
        given(jwt.getSubject()).willReturn(userId.toString());

        given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // WHEN
        var result = userService.updateUserSettings(settings, jwt);

        then(userRepository).should().save(argThat(user -> {
            assertThat(user).satisfies(u -> {
                assertThat(u.getFullName()).isEqualTo(settings.fullName());
                assertThat(u.getAvatarUrl()).isEqualTo(settings.avatarUrl());
            });
            return true;
        }));

        assertThat(result)
                .isNotNull()
                .extracting(UserResponse::fullName, UserResponse::avatarUrl)
                .containsExactly(settings.fullName(), settings.avatarUrl());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("No debería actualizar los datos del usuario por usar un nombre prohibido")
    void updateUserSettings_Name_Constraint() {
        settings = new UpdateUserSettings(
                "admin",
                "https://avatar.io/alex",
                false,
                false,
                false);

        try (MockedStatic<UserConstraints> mockedStatic = mockStatic(UserConstraints.class)) {
            mockedStatic.when(() -> UserConstraints.validateName("admin"))
                    .thenThrow(new ValidationException("Nombre prohibido"));

            assertThatThrownBy(() -> userService.updateUserSettings(settings, jwt))
                    .isInstanceOf(ValidationException.class);

            mockedStatic.verify(
                    () -> UserConstraints.validateName("admin"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("No debería actualizar avatar con URL de protocolo peligroso")
    void updateUserSettings_Url_DangerousProtocol() {
        settings = new UpdateUserSettings(
                "Alex Dev",
                "javascript:alert('xss')",
                false, false, false);

        try (MockedStatic<UrlConstraints> mockedStatic = mockStatic(UrlConstraints.class)) {

            mockedStatic.when(() -> UrlConstraints.validateUrl("javascript:alert('xss')"))
                    .thenThrow(new ValidationException("Protocolo no permitido"));

            assertThatThrownBy(() -> userService.updateUserSettings(settings, jwt))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Protocolo no permitido");

            mockedStatic.verify(
                    () -> UrlConstraints.validateUrl("javascript:alert('xss')"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("No debería actualizar avatar con URL apuntando a IP privada (SSRF)")
    void updateUserSettings_Url_PrivateIp() {
        settings = new UpdateUserSettings(
                "Alex Dev",
                "http://192.168.1.1/internal",
                false, false, false);

        try (MockedStatic<UrlConstraints> mockedStatic = mockStatic(UrlConstraints.class)) {

            mockedStatic.when(() -> UrlConstraints.validateUrl("http://192.168.1.1/internal"))
                    .thenThrow(new ValidationException("No se permiten URLs que apunten a direcciones IP privadas."));

            assertThatThrownBy(() -> userService.updateUserSettings(settings, jwt))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("IP privadas");

            mockedStatic.verify(
                    () -> UrlConstraints.validateUrl("http://192.168.1.1/internal"),
                    times(1)
            );
        }
    }
}