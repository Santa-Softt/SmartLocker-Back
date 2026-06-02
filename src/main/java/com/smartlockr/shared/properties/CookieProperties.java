package com.smartlockr.shared.properties;

import com.smartlockr.iam.domain.enums.SameSite;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

 
/**
 * Configuration properties for security cookies.
 * Values are loaded from the "security.cookies" prefix.
 * Fields:
 * - secure: whether cookies should be marked as Secure
 * - sameSite: SameSite attribute used when creating cookies
 */
@Validated
@ConfigurationProperties(prefix = "security.cookies")
public record CookieProperties(
        @NotNull boolean secure,
        @NotNull SameSite sameSite
) {
}

