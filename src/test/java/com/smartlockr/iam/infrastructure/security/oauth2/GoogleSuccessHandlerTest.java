package com.smartlockr.iam.infrastructure.security.oauth2;

import com.smartlockr.iam.application.auth.service.RefreshTokenService;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.security.jwt.JwtAdapter;
import com.smartlockr.iam.application.auth.service.AuthenticationService;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSuccessHandlerTest {

    // SUT
    @InjectMocks
    private GoogleSuccessHandler googleSuccessHandler;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private JwtAdapter jwtAdapter;
    @Mock
    private CookieFactory cookieFactory;
    @Mock
    private SecurityProperties securityProperties;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OidcUser oidcUser;

    private User user;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        user = new TestUser();
        user.setId(userId);
        user.setEmail("test_email@gmail.com");

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }


    @DisplayName("Debería generar cookies y redirigir cuando Google autoriza y el email está verificado")
    @Test
    void onAuthenticationSuccess_ok_flow() throws IOException {
        // given
        when(securityProperties.accessTtlDuration()).thenReturn(Duration.ofMinutes(5));
        when(securityProperties.refreshTtlDuration()).thenReturn(Duration.ofDays(7));
        when(securityProperties.oauthRedirectUri()).thenReturn("/home");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(authenticationService.findOrCreateUser(any(OidcUser.class))).thenReturn(user);

        String accessToken = "access-token-123";
        String refreshToken = "refresh-token-456";

        when(jwtAdapter.generateAccessToken(user)).thenReturn(accessToken);
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken);

        ResponseCookie accessCookie = ResponseCookie.from("auth_token", accessToken).build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken).build();

        when(cookieFactory.create("auth_token", accessToken, Duration.ofMinutes(5)))
                .thenReturn(accessCookie);
        when(cookieFactory.create("refresh_token", refreshToken, Duration.ofDays(7)))
                .thenReturn(refreshCookie);

        Authentication authentication =
                new TestingAuthenticationToken(oidcUser, null);

        // when
        googleSuccessHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        List<String> setCookieHeaders = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).hasSize(2);
        assertThat(setCookieHeaders.get(0)).contains("auth_token=" + accessToken);
        assertThat(setCookieHeaders.get(1)).contains("refresh_token=" + refreshToken);

        assertThat(response.getRedirectedUrl()).isEqualTo("/home");

        verify(authenticationService).findOrCreateUser(oidcUser);
        verify(refreshTokenService).createRefreshToken(user);
        verify(jwtAdapter).generateAccessToken(user);
        verify(cookieFactory).create("auth_token", accessToken, Duration.ofMinutes(5));
        verify(cookieFactory).create("refresh_token", refreshToken, Duration.ofDays(7));
        verify(securityProperties).accessTtlDuration();
        verify(securityProperties).refreshTtlDuration();
        verify(securityProperties).oauthRedirectUri();
        verifyNoMoreInteractions(authenticationService, refreshTokenService, jwtAdapter, cookieFactory, securityProperties);
    }

    @DisplayName("Debería fallar el login cuando el principal no es OidcUser")
    @Test
    void failed_login_flow_when_principal_is_not_oidcUser() {
        Authentication authentication =
                new TestingAuthenticationToken("not_oidc", null);

        Assertions.assertThrows(AuthenticationException.class,
                () -> googleSuccessHandler.onAuthenticationSuccess(request, response, authentication));

        verifyNoInteractions(authenticationService, refreshTokenService, jwtAdapter, cookieFactory);
    }

    @DisplayName("Debería fallar el login cuando el email no está verificado")
    @Test
    void failed_login_flow_when_email_not_verified() {
        when(oidcUser.getEmailVerified()).thenReturn(false);

        Authentication authentication =
                new TestingAuthenticationToken(oidcUser, null);

        Assertions.assertThrows(AuthenticationException.class,
                () -> googleSuccessHandler.onAuthenticationSuccess(request, response, authentication));

        verifyNoInteractions(authenticationService, refreshTokenService, jwtAdapter, cookieFactory);
    }

    static class TestUser extends User {
        public TestUser() { super(); }
    }
}