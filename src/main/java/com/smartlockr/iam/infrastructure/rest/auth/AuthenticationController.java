package com.smartlockr.iam.infrastructure.rest.auth;

import com.smartlockr.iam.application.auth.dto.AuthResponse;
import com.smartlockr.iam.application.auth.service.AuthenticationService;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.iam.infrastructure.rest.auth.dto.SessionResponse;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final SecurityProperties securityProperties;
    private final CookieFactory cookieFactory;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String AUTH_TOKEN_COOKIE_NAME = "auth_token";


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String rawRefreshToken, HttpServletResponse response) {
        authenticationService.revokeRefreshToken(rawRefreshToken);

        var authTokenCookie = authenticationService.clearCookies(AUTH_TOKEN_COOKIE_NAME);
        var refreshTokenCookie = authenticationService.clearCookies(REFRESH_TOKEN_COOKIE_NAME);

        response.addHeader(HttpHeaders.SET_COOKIE, authTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<SessionResponse> currentSession(@AuthenticationPrincipal Jwt jwt){
        var userId = jwt.getSubject();
        var secondsRemaining = Duration.between(Instant.now(), jwt.getExpiresAt());
        var response = authenticationService.getLoggedUserData(UUID.fromString(userId), secondsRemaining.getSeconds());
        return ResponseEntity.ok(response);
    }

    /**
     * REFRESH: Rota la sesión.
     * Genera DOS cookies HttpOnly usando tu Factory.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshToken(@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String oldRefreshToken, HttpServletResponse response) {
        AuthResponse authData = authenticationService.refreshSession(oldRefreshToken);

        var refreshCookie = cookieFactory.create(REFRESH_TOKEN_COOKIE_NAME, authData.refreshToken(), securityProperties.refreshTtlDuration());
        var accessCookie = cookieFactory.create(AUTH_TOKEN_COOKIE_NAME, authData.accessToken(), securityProperties.accessTtlDuration());

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        return ResponseEntity.noContent().build();
    }

}
