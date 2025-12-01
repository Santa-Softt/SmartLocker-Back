package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.AuthResponse;
import com.smartlockr.iam.application.auth.dto.RotationResult;
import com.smartlockr.iam.application.auth.port.JwtProvider;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.SessionResponse;
import com.smartlockr.iam.infrastructure.rest.auth.dto.TokenDetails;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CookieFactory cookieFactory;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public User findOrCreateUser(OidcUser user) {
        return userRepository.findByEmail(user.getEmail())
                .map(existingUser -> {
                    existingUser.setFullName(user.getFullName());
                    existingUser.setAvatarUrl(user.getPicture());
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = userMapper.toNewUser(user);
                    return userRepository.save(newUser);
                });
    }

    public ResponseCookie clearCookies(String name){
        return cookieFactory.clean(name);
    }

    @Validated
    public SessionResponse getLoggedUserData(@NotNull UUID id, Instant expiresAt) {
        var user =  userMapper.toUserResponse(userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("El usuario no existe")));
        return new SessionResponse(user, new TokenDetails(expiresAt));
    }

    @Transactional
    public AuthResponse refreshSession(String oldRawRefreshToken) {
        // 1. Rotación (DB Layer)
        RotationResult result = refreshTokenService.rotateRefreshToken(oldRawRefreshToken);

        // 2. Generación JWT (Crypto Layer)1
        String newAccessToken = jwtProvider.generateAccessToken(result.user());

        // 3. Empaquetado (Transport Layer)
        return new AuthResponse(newAccessToken, result.newRawRefreshToken());
    }

    /**
     * Cierra la sesión revocando el Refresh Token persistente.
     * Es una operación "fire and forget" (si el token es inválido, no falla).
     */
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        refreshTokenService.revokeToken(rawRefreshToken);
    }
}
