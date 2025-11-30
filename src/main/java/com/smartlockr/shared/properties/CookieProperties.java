package com.smartlockr.shared.properties;

import com.smartlockr.iam.domain.enums.SameSite;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.cookies")
public record CookieProperties(
        @NotNull boolean secure,
        @NotNull SameSite sameSite
) {
}

