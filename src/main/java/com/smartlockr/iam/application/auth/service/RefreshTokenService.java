package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.RotationResult;
import com.smartlockr.iam.domain.ports.TokenHasher;
import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.RefreshTokenRepository;
import com.smartlockr.iam.infrastructure.security.error.exception.SecurityTokenReuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFactory refreshTokenFactory;
    private final TokenHasher tokenHasher;

    /**
     * Crea un refresh token inicial (login/signup)
     */
    @Transactional
    public String createRefreshToken(User user) {
        var userId = user.getId();
        Instant actualTime = Instant.now();
        if(refreshTokenRepository.existsActiveTokenForUser(userId,actualTime))
            refreshTokenRepository.revokeAllActiveTokensForUser(user);
        var creation = refreshTokenFactory.createFor(user);
        refreshTokenRepository.save(creation.entity());
        return creation.rawToken();
    }

    /**
     * ROTACIÓN DE TOKEN (Crítico)
     * Recibe el token crudo (cookie), lo valida, lo revoca y emite uno nuevo.
     */
    @Transactional(noRollbackFor = {SecurityTokenReuseException.class})
    public RotationResult rotateRefreshToken(String rawToken) {
        // 1. Hash & Search
        String tokenHash = tokenHasher.hash(rawToken);
        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new NoSuchElementException("El refresh token es invalido"));

        // 2. Reuse Detection
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveTokensForUser(currentToken.getUser());
            throw new SecurityTokenReuseException("Se detectó un intento de usar un refresh token ya utilizado.");
        }

        // 3. Expiration Check
        if (currentToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Refresh token expirado");
        }

        // 4. Rotate
        currentToken.setLastUsedAt(Instant.now());
        currentToken.setRevoked(true);

        var newCreation = refreshTokenFactory.createFor(currentToken.getUser());
        refreshTokenRepository.save(newCreation.entity());

        return new RotationResult(
                newCreation.rawToken(),
                currentToken.getUser()
        );
    }
    /**
     * Revocación manual (Logout)
     */
    @Transactional
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;

        String tokenHash = tokenHasher.hash(rawToken);

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setLastUsedAt(Instant.now());
                });
    }

    /**
     * Caso de Uso: Limpieza de mantenimiento.
     */
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(Instant.now());
    }
}
