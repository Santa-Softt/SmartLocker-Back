package com.smartlockr.iam.infrastructure.security.factory;

import com.smartlockr.iam.application.properties.CookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class CookieFactory {
    private final CookieProperties cookieProperties;

    public ResponseCookie create(String name, String token, Duration maxAge) {
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(maxAge)
                .sameSite(cookieProperties.sameSite().attribute())
                .build();
    }

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
