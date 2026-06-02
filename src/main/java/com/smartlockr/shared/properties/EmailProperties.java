package com.smartlockr.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email notification configuration.
 * SMTP transport is configured through Spring's standard spring.mail.* properties.
 */
@Validated
@ConfigurationProperties(prefix = "smartlockr.email")
public record EmailProperties(
        boolean enabled,
        String from,
        String supportAddress,
        String brandName
) {
    private static final String DEFAULT_FROM = "no-reply@smartlockr.com";
    private static final String DEFAULT_SUPPORT = "support@smartlockr.com";
    private static final String DEFAULT_BRAND_NAME = "SmartLockr";

    public EmailProperties {
        if (from == null || from.isBlank()) {
            from = DEFAULT_FROM;
        }
        if (supportAddress == null || supportAddress.isBlank()) {
            supportAddress = DEFAULT_SUPPORT;
        }
        if (brandName == null || brandName.isBlank()) {
            brandName = DEFAULT_BRAND_NAME;
        }
    }
}
