package com.smartlockr.iam.infrastructure.persistence.repository;

import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Búsqueda O(1) usando el índice definido en la Entidad
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Kill switch en caso de detección de robo
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user and r.revoked = false")
    void revokeAllActiveTokensForUser(User user);

    // Borrado Físico Masivo (Hard Delete) de tokens expirados.
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    void deleteExpiredTokens(Instant now);

    /**
     * Verifica si existe ALGÚN token activo (ni revocado ni expirado) para el usuario.
     * Útil para políticas de "Single Session" o para evitar generar basura si ya tiene uno válido.
     */
    @Query("""
        SELECT COUNT(r) > 0
        FROM RefreshToken r
        WHERE r.user.id = :userId
          AND r.revoked = false
          AND r.expiresAt > :now
    """)
    boolean existsActiveTokenForUser(UUID userId, Instant now);
}
