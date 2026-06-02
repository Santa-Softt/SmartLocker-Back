package com.smartlockr.iam.infrastructure.rest.auth;

import com.smartlockr.iam.application.auth.dto.AuthResponse;
import com.smartlockr.iam.application.auth.service.AuthenticationService;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.iam.infrastructure.security.factory.CookieFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private SecurityProperties securityProperties;

    @MockitoBean
    private CookieFactory cookieFactory;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String AUTH_COOKIE_NAME = "auth_token";

    @Test
    @DisplayName("POST /auth/logout - Should return 204 and clear cookies structure")
    void logout_Integration() throws Exception {
        // Arrange
        String token = "valid-token";
        // Simulamos cookies de borrado (Max-Age=0)
        ResponseCookie clearAuthCookie = ResponseCookie.from(AUTH_COOKIE_NAME, "").maxAge(0).build();
        ResponseCookie clearRefreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "").maxAge(0).build();

        given(authenticationService.clearCookies(AUTH_COOKIE_NAME)).willReturn(clearAuthCookie);
        given(authenticationService.clearCookies(REFRESH_COOKIE_NAME)).willReturn(clearRefreshCookie);

        // Act & Assert
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, token)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(AUTH_COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(AUTH_COOKIE_NAME, 0))
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        then(authenticationService).should().revokeRefreshToken(token);
    }

    @Test
    @DisplayName("POST /auth/logout - Should return 400 Bad Request when mandatory cookie is missing")
    void logout_MissingCookie_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/refresh - Should rotate tokens and set strictly configured cookies")
    void refreshToken_Integration() throws Exception {
        // Arrange
        String oldToken = "old-token";
        String newAccess = "new-jwt-access";
        String newRefresh = "new-jwt-refresh";
        long refreshTtlSeconds = 3600;
        long accessTtlSeconds = 300;

        AuthResponse authResponse = new AuthResponse(newAccess, newRefresh);

        // Cookies esperadas con atributos de seguridad
        ResponseCookie accessCookie = ResponseCookie.from(AUTH_COOKIE_NAME, newAccess)
                .path("/")
                .httpOnly(true)
                .maxAge(accessTtlSeconds)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, newRefresh)
                .path("/auth")
                .httpOnly(true)
                .maxAge(refreshTtlSeconds)
                .build();

        // BDD Stubbing
        given(authenticationService.refreshSession(oldToken)).willReturn(authResponse);
        given(securityProperties.accessTtlDuration()).willReturn(Duration.ofSeconds(accessTtlSeconds));
        given(securityProperties.refreshTtlDuration()).willReturn(Duration.ofSeconds(refreshTtlSeconds));

        given(cookieFactory.create(eq(REFRESH_COOKIE_NAME), eq(newRefresh), any())).willReturn(refreshCookie);
        given(cookieFactory.create(eq(AUTH_COOKIE_NAME), eq(newAccess), any())).willReturn(accessCookie);

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, oldToken)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, newRefresh))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().path(REFRESH_COOKIE_NAME, "/auth"))

                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, (int) refreshTtlSeconds))
                .andExpect(cookie().value(AUTH_COOKIE_NAME, newAccess))
                .andExpect(cookie().httpOnly(AUTH_COOKIE_NAME, true));
    }


}