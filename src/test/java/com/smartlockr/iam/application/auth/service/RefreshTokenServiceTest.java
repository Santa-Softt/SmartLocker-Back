package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.CreatedRefreshToken;
import com.smartlockr.iam.application.auth.dto.RotationResult;
import com.smartlockr.iam.domain.ports.TokenHasher;
import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.RefreshTokenRepository;
import com.smartlockr.iam.infrastructure.security.error.exception.SecurityTokenReuseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenFactory refreshTokenFactory;
    @Mock
    private TokenHasher tokenHasher;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Nested
    @DisplayName("UseCase: Create Initial Token")
    class CreateTokenTests {

        @Test
        @DisplayName("Should revoke existing tokens if active ones exist")
        void create_WhenActiveExists_ShouldRevokeAndCreate() {
            // Arrange
            User user = new TestUser();
            String rawToken = "uuid-raw";
            RefreshToken newTokenEntity = new RefreshToken();
            CreatedRefreshToken creation = new CreatedRefreshToken(newTokenEntity, rawToken);

            when(refreshTokenRepository.existsActiveTokenForUser(eq(user.getId()), any(Instant.class)))
                    .thenReturn(true);
            when(refreshTokenFactory.createFor(user)).thenReturn(creation);

            // Act
            String result = refreshTokenService.createRefreshToken(user);

            // Assert
            assertThat(result).isEqualTo(rawToken);

            verify(refreshTokenRepository).revokeAllActiveTokensForUser(user);
            verify(refreshTokenRepository).save(newTokenEntity);
        }

        @Test
        @DisplayName("Should skip revocation if no active tokens exist")
        void create_WhenNoneActive_ShouldOnlyCreate() {
            // Arrange
            User user = new TestUser();
            String rawToken = "uuid-raw";
            CreatedRefreshToken creation = new CreatedRefreshToken(new RefreshToken(), rawToken);

            when(refreshTokenRepository.existsActiveTokenForUser(eq(user.getId()), any(Instant.class)))
                    .thenReturn(false);
            when(refreshTokenFactory.createFor(user)).thenReturn(creation);

            // Act
            refreshTokenService.createRefreshToken(user);

            // Assert
            verify(refreshTokenRepository, never()).revokeAllActiveTokensForUser(any());
            verify(refreshTokenRepository).save(any());
        }
    }

    @Nested
    @DisplayName("UseCase: Rotate Token (Critical)")
    class RotateTokenTests {

        @Test
        @DisplayName("Success: Should rotate valid token")
        void rotate_ValidToken_ShouldSuccess() {
            // Arrange
            String rawToken = "valid-raw";
            String hash = "hashed-token";
            User user = new TestUser();

            // Token actual válido (No revocado, No expirado)
            RefreshToken currentToken = spy(new RefreshToken());
            currentToken.setUser(user);
            currentToken.setRevoked(false);
            currentToken.setExpiresAt(Instant.now().plusSeconds(3600));

            // Nuevo Token
            String newRaw = "new-raw";
            RefreshToken newTokenEntity = new RefreshToken();
            CreatedRefreshToken newCreation = new CreatedRefreshToken(newTokenEntity, newRaw);

            when(tokenHasher.hash(rawToken)).thenReturn(hash);
            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(currentToken));
            when(refreshTokenFactory.createFor(user)).thenReturn(newCreation);

            // Act
            RotationResult result = refreshTokenService.rotateRefreshToken(rawToken);

            // Assert
            assertThat(result.newRawRefreshToken()).isEqualTo(newRaw);

            // Verificaciones de estado en el objeto spy
            verify(currentToken).setRevoked(true);
            verify(currentToken).setLastUsedAt(any(Instant.class));

            verify(refreshTokenRepository).save(newTokenEntity);
        }

        @Test
        @DisplayName("Security: Reuse Detection should trigger massive revocation")
        void rotate_WhenReuseDetected_ShouldThrowAndRevokeAll() {
            // Arrange
            String rawToken = "stolen-raw";
            String hash = "hash";
            User user = new TestUser();

            RefreshToken stolenToken = new RefreshToken();
            stolenToken.setUser(user);
            stolenToken.setRevoked(true); // YA USADO -> Reuse Attempt

            when(tokenHasher.hash(rawToken)).thenReturn(hash);
            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stolenToken));

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                    .isInstanceOf(SecurityTokenReuseException.class)
                    .hasMessageContaining("ya utilizado");

            // CRITICAL ASSERTION: El servicio debe "matar" todas las sesiones del usuario
            verify(refreshTokenRepository).revokeAllActiveTokensForUser(user);

            // No debe intentar crear uno nuevo
            verifyNoInteractions(refreshTokenFactory);
        }

        @Test
        @DisplayName("Fail: Should throw if token expired")
        void rotate_WhenExpired_ShouldThrow() {
            // Arrange
            String rawToken = "expired-raw";
            RefreshToken expiredToken = new RefreshToken();
            expiredToken.setRevoked(false);
            expiredToken.setExpiresAt(Instant.now().minusSeconds(10)); // Pasado

            when(tokenHasher.hash(rawToken)).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expiredToken));

            // Act & Assert
            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expirado");

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fail: Should throw if token not found")
        void rotate_WhenNotFound_ShouldThrow() {
            when(tokenHasher.hash(anyString())).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("unknown"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("UseCase: Manual Revocation")
    class RevokeTests {

        @Test
        @DisplayName("Should revoke found token")
        void revoke_WhenFound_ShouldSetFlag() {
            // Arrange
            String raw = "token";
            RefreshToken token = spy(new RefreshToken());

            when(tokenHasher.hash(raw)).thenReturn("hash");
            when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

            // Act
            refreshTokenService.revokeToken(raw);

            // Assert
            verify(token).setRevoked(true);
            verify(token).setLastUsedAt(any(Instant.class));
            // JPA Dirty checking usually saves, but verify logic flow
        }

        @Test
        @DisplayName("Should do nothing on null input")
        void revoke_NullInput_ShouldExit() {
            refreshTokenService.revokeToken(null);
            verifyNoInteractions(tokenHasher, refreshTokenRepository);
        }
    }

    @Nested
    @DisplayName("UseCase: Maintenance")
    class MaintenanceTests {

        @Test
        @DisplayName("Should trigger deletion with current timestamp")
        void deleteExpiredTokens_ShouldCallRepository() {
            // Act
            refreshTokenService.deleteExpiredTokens();

            // Assert
            verify(refreshTokenRepository).deleteExpiredTokens(any(Instant.class));
        }
    }

    // Helper Stub
    static class TestUser extends User {
        public TestUser() { super(); }
    }
}