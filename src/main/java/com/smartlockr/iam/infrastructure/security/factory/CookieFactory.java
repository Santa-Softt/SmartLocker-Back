package com.smartlockr.iam.infrastructure.security.factory;
import com.smartlockr.shared.properties.CookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Factory for creating and clearing authentication-related cookies.
 * Responsibilities:
 * - Creates an {@link ResponseCookie} with common security attributes (HttpOnly, Secure, SameSite).
 * - Clears a cookie by emitting the same cookie with an empty value and max-age set to zero.
 */
@Component
@RequiredArgsConstructor
public class CookieFactory {
    private final CookieProperties cookieProperties;

    /**
     * Creates a cookie with the provided name and token value.
     * The cookie is always HttpOnly, uses the configured Secure and SameSite attributes, and
     * applies the provided max-age.
     * @param name cookie name
     * @param token cookie value
     * @param maxAge cookie max age
     * @return a built {@link ResponseCookie}
     */
    public ResponseCookie create(String name, String token, Duration maxAge) {
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(maxAge)
                .sameSite(cookieProperties.sameSite().attribute())
                .build();
    }

    /**
     * Clears a cookie by setting an empty value and a max-age of zero.
     * Uses the same HttpOnly, Secure, Path and SameSite attributes to ensure the client overwrites
     * the existing cookie.
     * @param name cookie name
     * @return a built {@link ResponseCookie} that instructs the client to remove the cookie
     */
    public ResponseCookie clean(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(0)
                .sameSite(cookieProperties.sameSite().attribute())
                .build();
    }
}
