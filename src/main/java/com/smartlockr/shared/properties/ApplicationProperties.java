package com.smartlockr.shared.properties;

import jakarta.validation.constraints.NotEmpty;
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
        Set<String> allowedOrigins
) {
}
