package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.CreatedRefreshToken;
import com.smartlockr.iam.application.properties.SecurityProperties;
import com.smartlockr.iam.domain.ports.TokenHasher;
import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public final class RefreshTokenFactory {

    private final TokenHasher tokenHasher;
    private final SecurityProperties securityProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public CreatedRefreshToken createFor(User user){
        var rawToken = generateSecureToken(securityProperties.refreshTokenByteSize());

        var now = Instant.now();

        var refreshToken =  RefreshToken.builder()
                .tokenHash(tokenHasher.hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(securityProperties.refreshTtlDuration()))
                .user(user)
                .revoked(false)
                .build();

        return new CreatedRefreshToken(refreshToken, rawToken);
    }

    private String generateSecureToken(int size){
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
