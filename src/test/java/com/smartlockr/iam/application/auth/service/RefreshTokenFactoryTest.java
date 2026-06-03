package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.CreatedRefreshToken;
import com.smartlockr.iam.domain.ports.TokenHasher;
import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.shared.properties.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFactoryTest {

    @Mock
    private TokenHasher tokenHasher;

    private SecurityProperties securityProperties;
    private RefreshTokenFactory refreshTokenFactory;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties(
                false,
                "dGVzdC1zZWNyZXQ=",
                "test-issuer",
                "test-audience",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                32,
                "http://localhost/callback",
                "admin@test.com"
        );
        refreshTokenFactory = new RefreshTokenFactory(tokenHasher, securityProperties);
    }

    @Test
    @DisplayName("createFor - genera un token con el tamano correcto, hasheado y con expiracion")
    void shouldCreateRefreshTokenWithCorrectShape() {
        // GIVEN
        User user = User.builder()
                .id(com.smartlockr.shared.utils.UuidV7.generate())
                .email("user@test.local")
                .fullName("Test User")
                .build();
        given(tokenHasher.hash(org.mockito.ArgumentMatchers.anyString())).willReturn("hashed-token-value");

        // WHEN
        CreatedRefreshToken result = refreshTokenFactory.createFor(user);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.rawToken()).isNotNull();
        assertThat(result.rawToken()).isNotBlank();

        // Verifica formato URL-safe Base64 (32 bytes -> 43 chars sin padding)
        assertThat(result.rawToken()).hasSize(43);
        assertThat(result.rawToken()).matches("^[A-Za-z0-9_-]+$");

        RefreshToken entity = result.entity();
        assertThat(entity.getTokenHash()).isEqualTo("hashed-token-value");
        assertThat(entity.getUser()).isEqualTo(user);
        assertThat(entity.isRevoked()).isFalse();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getExpiresAt()).isAfter(entity.getCreatedAt());

        verify(tokenHasher).hash(result.rawToken());
    }

    @Test
    @DisplayName("createFor - genera tokens unicos en cada invocacion")
    void shouldGenerateUniqueTokensOnEachCall() {
        // GIVEN
        User user = User.builder()
                .id(com.smartlockr.shared.utils.UuidV7.generate())
                .email("user@test.local")
                .build();
        given(tokenHasher.hash(org.mockito.ArgumentMatchers.anyString())).willReturn("hashed");

        // WHEN
        CreatedRefreshToken first = refreshTokenFactory.createFor(user);
        CreatedRefreshToken second = refreshTokenFactory.createFor(user);

        // THEN
        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
    }

    @Test
    @DisplayName("createFor - el tamano del token respeta refreshTokenByteSize de la configuracion")
    void shouldRespectConfiguredByteSize() {
        // GIVEN - 64 bytes produce 86 chars en Base64 URL sin padding
        SecurityProperties bigProps = new SecurityProperties(
                false, "secret", "iss", "aud",
                Duration.ofMinutes(15), Duration.ofDays(7),
                64, "http://localhost", "admin@test.com");
        RefreshTokenFactory factory = new RefreshTokenFactory(tokenHasher, bigProps);
        User user = User.builder().id(com.smartlockr.shared.utils.UuidV7.generate()).email("u@e.com").build();
        given(tokenHasher.hash(org.mockito.ArgumentMatchers.anyString())).willReturn("h");

        // WHEN
        CreatedRefreshToken result = factory.createFor(user);

        // THEN
        assertThat(result.rawToken()).hasSize(86);
    }
}
