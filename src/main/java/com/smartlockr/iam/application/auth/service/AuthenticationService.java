package com.smartlockr.iam.application.auth.service;

import com.smartlockr.iam.application.auth.dto.AuthResponse;
import com.smartlockr.iam.application.auth.dto.RotationResult;
import com.smartlockr.iam.application.auth.port.JwtProvider;
import com.smartlockr.iam.application.mapper.UserMapper;
import com.smartlockr.iam.application.service.UserService;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.iam.infrastructure.rest.auth.dto.SessionResponse;
import com.smartlockr.iam.infrastructure.rest.auth.dto.TokenDetails;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import com.smartlockr.shared.utils.CacheNames;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.UUID;

/**
 * Service responsible for handling authentication operations including user
 * creation, session management, token refresh, and token revocation.
 */
@Service
@RequiredArgsConstructor
@Validated
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CookieFactory cookieFactory;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    /**
     * Finds an existing user by email or creates a new user from OIDC authentication.
     * For non-admin users, updates full name and avatar URL. For admin users,
     * only updates the avatar URL.
     *
     * @param oidcUser the OIDC user containing authentication information
     * @return the existing or newly created user
     */
    @Transactional
    @CacheEvict(value = CacheNames.USER_CACHE, key = "#result.id")
    public User findOrCreateUser(OidcUser oidcUser) {
        return userRepository.findByEmail(oidcUser.getEmail())
                .map(user -> updateUserInfo(user, oidcUser))
                .orElseGet(() -> userRepository.save(userMapper.toNewUser(oidcUser)));
    }

    private User updateUserInfo(User user, OidcUser oidcUser) {
        user.setAvatarUrl(oidcUser.getPicture());
        return user;
    }

    /**
     * Creates a cookie to clear authentication data by name.
     *
     * @param name the name of the cookie to clear
     * @return the response cookie configured to clear the specified cookie
     */
    public ResponseCookie clearCookies(String name){
        return cookieFactory.clean(name);
    }

    /**
     * Retrieves logged user data along with token details.
     *
     * @param id the user identifier
     * @param expiresAt the expiration time of the token
     * @return session response containing user data and token details
     * @throws UsernameNotFoundException if the user does not exist
     */
    public SessionResponse getLoggedUserData(@NotNull UUID id, Instant expiresAt) {
        return new SessionResponse(userService.getUserResponse(id), new TokenDetails(expiresAt));
    }

    /**
     * Refreshes the user session by rotating the refresh token and generating
     * a new access token.
     *
     * @param oldRawRefreshToken the current refresh token to be rotated
     * @return authentication response containing the new access token and
     *         the new refresh token
     */
    @Transactional
    public AuthResponse refreshSession(String oldRawRefreshToken) {
        RotationResult result = refreshTokenService.rotateRefreshToken(oldRawRefreshToken);

        String newAccessToken = jwtProvider.generateAccessToken(result.user());

        return new AuthResponse(newAccessToken, result.newRawRefreshToken());
    }

    /**
     * Revokes the refresh token to log out the user. This is a fire-and-forget
     * operation that does not fail if the token is already invalid.
     *
     * @param rawRefreshToken the refresh token to revoke
     */
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        refreshTokenService.revokeToken(rawRefreshToken);
    }
}
