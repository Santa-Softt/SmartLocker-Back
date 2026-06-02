package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.AuthResponse;
import com.smartlockr.iam.application.auth.dto.RotationResult;
import com.smartlockr.iam.application.auth.port.JwtProvider;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.application.service.UserService;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.SessionResponse;
import com.smartlockr.iam.infrastructure.rest.auth.dto.UserResponse;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import com.smartlockr.rental.application.service.RentalService;
import com.smartlockr.shared.email.EmailNotificationSender;
import com.smartlockr.shared.email.WelcomeEmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailNotificationSender emailNotificationSender;
    @Mock
    private RentalService rentalService;
    @Mock
    private CookieFactory cookieFactory;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Mock
    private UserService userService;

    @Nested
    @DisplayName("Operation: Find Or Create User")
    class FindOrCreateUserTests {

        @Test
        @DisplayName("Should update existing user when OIDC email matches")
        void findOrCreateUser_WhenUserExists_ShouldUpdateFields() {
            // Arrange
            String email = "test@smartlockr.com";
            OidcUser oidcUser = mockOidcUser(email, "New Name", "https://new-avatar.com");
            var userPreferences = UserPreferences.builder()
                    .receiveReceipts(true)
                    .receivesPromotions(true)
                    .build();

            User existingUser = new TestUser();
            existingUser.setEmail(email);
            existingUser.setFullName("Old Name");
            existingUser.setAvatarUrl("https://old.com");
            existingUser.setUserPreferences(userPreferences);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

            // Act
            User result = authenticationService.findOrCreateUser(oidcUser);

            // Assert State
            assertThat(result.getFullName()).isEqualTo("Old Name");
            assertThat(result.getAvatarUrl()).isEqualTo("https://new-avatar.com");
            assertThat(result.getUserPreferences()).isEqualTo(userPreferences);

            // Assert Interactions (Classic)
            // Verificamos que NUNCA se llame a save (Dirty Checking se encarga)
            verify(userRepository, never()).save(any());

            // Verificamos que NO se usó el mapper (pues el usuario ya existía)
            verifyNoInteractions(userMapper);
            verifyNoInteractions(emailNotificationSender);
        }

        @Test
        @DisplayName("Should create and persist new user when OIDC email is missing")
        void findOrCreateUser_WhenUserNew_ShouldSave() {
            // Arrange
            String email = "new@smartlockr.com";
            OidcUser oidcUser = mockOidcUser(email, "New User", "https://avatar.com");
            User newUser = new TestUser();
            newUser.setEmail(email);

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userMapper.toNewUser(oidcUser)).thenReturn(newUser);
            when(userRepository.save(newUser)).thenReturn(newUser);

            // Act
            User result = authenticationService.findOrCreateUser(oidcUser);

            // Assert
            assertThat(result).isNotNull();

            verify(userRepository).save(newUser);
            verify(userMapper).toNewUser(oidcUser);
            verify(emailNotificationSender).sendWelcomeEmail(any(WelcomeEmailMessage.class));
        }
    }

    @Nested
    @DisplayName("Operation: Refresh Session")
    class RefreshSessionTests {

        @Test
        @DisplayName("Should orchestrate rotation and token generation")
        void refreshSession_Success() {
            // Arrange
            String oldToken = "old-refresh";
            String newToken = "new-refresh";
            String jwt = "new-access-jwt";
            User user = new TestUser();

            RotationResult rotationResult = new RotationResult(newToken, user);

            when(refreshTokenService.rotateRefreshToken(oldToken)).thenReturn(rotationResult);
            when(jwtProvider.generateAccessToken(user)).thenReturn(jwt);

            // Act
            AuthResponse response = authenticationService.refreshSession(oldToken);

            // Assert
            assertThat(response.accessToken()).isEqualTo(jwt);
            assertThat(response.refreshToken()).isEqualTo(newToken);

            verify(refreshTokenService).rotateRefreshToken(oldToken);
            verify(jwtProvider).generateAccessToken(user);

            // Verificamos que NO se toque el repositorio de usuarios directamente aquí
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    @DisplayName("Operation: Get Logged User Data")
    class GetLoggedUserDataTests {

        @Test
        @DisplayName("Should throw exception when user ID not found via UserService")
        void getLoggedUserData_NotFound() {
            UUID id = UUID.randomUUID();
            Instant expiresAt = Instant.now().plusSeconds(100L);

            when(userService.getUserResponse(id)).thenThrow(new UsernameNotFoundException("El usuario no existe"));

            assertThatThrownBy(() -> authenticationService.getLoggedUserData(id, expiresAt))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("El usuario no existe");

            verify(userService).getUserResponse(id);
        }

        @Test
        @DisplayName("Should return session data when user exists via UserService")
        void getLoggedUserData_Success() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant expiresAt = Instant.now().plusSeconds(300L);

            UserResponse userResponse = new UserResponse(
                    id,
                    "Test User",
                    "test@email.com",
                    "avatar.png",
                    Role.CONSUMER,
                    true,
                    false,
                    null,
                    true,
                    false
            );

            when(userService.getUserResponse(id)).thenReturn(userResponse);

            // Act
            SessionResponse response = authenticationService.getLoggedUserData(id, expiresAt);

            // Assert
            assertThat(response.userResponse()).isEqualTo(userResponse);
            assertThat(response.tokenDetails().expiresAt()).isEqualTo(expiresAt);

            verify(userService).getUserResponse(id);
        }
    }

    private OidcUser mockOidcUser(String email, String name, String picture) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getEmail()).thenReturn(email);
        lenient().when(oidcUser.getFullName()).thenReturn(name);
        lenient().when(oidcUser.getPicture()).thenReturn(picture);
        return oidcUser;
    }

    /**
     * Stub class to bypass 'protected' constructor access restriction on the Entity.
     * This avoids using reflection or modifying the production entity.
     * Safe for Production: This code never leaves the src/test folder.
     */
    static class TestUser extends User {
        public TestUser() {
            super();
        }
    }

    @Nested
    @DisplayName("Operation: Synchronize Suspension")
    class SynchronizeSuspensionTests {

        @Test
        @DisplayName("Should suspend user when rentalService reports pending penalty")
        void synchronizeSuspension_WithPenalty() {
            UUID userId = UUID.randomUUID();
            User existingUser = new TestUser();
            existingUser.setId(userId);
            existingUser.setEmail("test@smartlockr.com");
            existingUser.setFullName("Test");
            existingUser.setUserPreferences(UserPreferences.builder()
                    .receiveReceipts(true)
                    .receivesPromotions(true)
                    .build());

            when(userRepository.findByEmail("test@smartlockr.com")).thenReturn(Optional.of(existingUser));
            when(rentalService.hasPenalizedRentalForUser(userId)).thenReturn(true);

            OidcUser oidcUser = mockOidcUser("test@smartlockr.com", "Test", "pic.png");

            User result = authenticationService.findOrCreateUser(oidcUser);

            assertThat(result.isSuspended()).isTrue();
            assertThat(result.getSuspensionTime()).isNotNull();
        }

        @Test
        @DisplayName("Should unsuspend user when rentalService reports no penalty")
        void synchronizeSuspension_NoPenalty() {
            UUID userId = UUID.randomUUID();
            User existingUser = new TestUser();
            existingUser.setId(userId);
            existingUser.setEmail("test@smartlockr.com");
            existingUser.setFullName("Test");
            existingUser.setSuspended(true);
            existingUser.setSuspensionTime(Instant.now());
            existingUser.setUserPreferences(UserPreferences.builder()
                    .receiveReceipts(true)
                    .receivesPromotions(true)
                    .build());

            when(userRepository.findByEmail("test@smartlockr.com")).thenReturn(Optional.of(existingUser));
            when(rentalService.hasPenalizedRentalForUser(userId)).thenReturn(false);

            OidcUser oidcUser = mockOidcUser("test@smartlockr.com", "Test", "pic.png");

            User result = authenticationService.findOrCreateUser(oidcUser);

            assertThat(result.isSuspended()).isFalse();
            assertThat(result.getSuspensionTime()).isNull();
        }
    }

    @Nested
    @DisplayName("Operation: Clear Cookies")
    class ClearCookiesTests {

        @Test
        @DisplayName("Should delegate to CookieFactory.clean")
        void shouldDelegateToCookieFactory() {
            ResponseCookie expected = ResponseCookie.from("auth_token", "")
                    .path("/")
                    .maxAge(0)
                    .build();
            when(cookieFactory.clean("auth_token")).thenReturn(expected);

            ResponseCookie result = authenticationService.clearCookies("auth_token");

            assertThat(result).isEqualTo(expected);
            verify(cookieFactory).clean("auth_token");
        }
    }

    @Nested
    @DisplayName("Operation: Revoke Refresh Token")
    class RevokeRefreshTokenTests {

        @Test
        @DisplayName("Should delegate revocation to RefreshTokenService")
        void shouldDelegateToRefreshTokenService() {
            authenticationService.revokeRefreshToken("some-token");

            verify(refreshTokenService).revokeToken("some-token");
        }
    }
}
