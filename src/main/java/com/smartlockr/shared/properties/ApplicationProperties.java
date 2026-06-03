package com.smartlockr.shared.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;


/**
 * Root application configuration properties.
 * Values are loaded from the "smartlockr" prefix.
 * Fields:
 * - allowedOrigins: allowed CORS origins
 */
@Validated
@ConfigurationProperties(prefix = "smartlockr")
public record ApplicationProperties(
        @NotEmpty
        Set<@NotBlank String> allowedOrigins,
        @NotNull ToggleProperties scheduling
) {
    public ApplicationProperties {
        if (allowedOrigins != null && allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalArgumentException("CORS wildcard '*' is not allowed when credentials are enabled");
        }
    }

    public record ToggleProperties(boolean enabled) {
    }
}
