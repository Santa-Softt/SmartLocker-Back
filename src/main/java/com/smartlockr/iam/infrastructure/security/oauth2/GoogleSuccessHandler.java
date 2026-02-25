package com.smartlockr.iam.infrastructure.security.oauth2;

import com.smartlockr.iam.application.auth.service.RefreshTokenService;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.iam.infrastructure.security.jwt.JwtAdapter;
import com.smartlockr.iam.application.auth.service.AuthenticationService;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;

 
/**
 * OAuth2 success handler for Google OpenID Connect logins.
 * On success, it ensures the user's email is verified, creates the user if needed,
 * issues refresh and access tokens, stores them as cookies, and redirects to the configured URI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationService authenticationService;
    private final JwtAdapter jwtAdapter;
    private final CookieFactory cookieFactory;
    private final SecurityProperties securityProperties;
    private final RefreshTokenService refreshTokenService;

 
    /**
     * Called by Spring Security after a successful OAuth2 authentication.
     * Expects an {@link OidcUser} principal and rejects the flow if email is not verified.
     * @param request current HTTP request
     * @param response current HTTP response
     * @param authentication authentication information
     * @throws IOException when the response cannot be written
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser))
            throw new AuthenticationException("No se pudo encontrar una instancia OpenID");
        var isEmailVerified = oidcUser.getEmailVerified();
        if(Boolean.FALSE.equals(isEmailVerified))
            throw new AuthenticationException("El email debe estar verificado para registrarse");

        var user = authenticationService.findOrCreateUser(oidcUser);

        var refreshToken = refreshTokenService.createRefreshToken(user);
        var accessToken = jwtAdapter.generateAccessToken(user);

        var cookieAccessToken = cookieFactory.create("auth_token",
                accessToken,
                securityProperties.accessTtlDuration()).toString();
        var cookieRefreshToken = cookieFactory.create("refresh_token",
                refreshToken,
                securityProperties.refreshTtlDuration()).toString();

        response.addHeader(HttpHeaders.SET_COOKIE, cookieAccessToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieRefreshToken);
        response.sendRedirect(securityProperties.oauthRedirectUri());
    }
}
