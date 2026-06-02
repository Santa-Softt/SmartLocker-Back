package com.smartlockr.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Redis configuration properties.
 * Maps values defined in application.yml under the 'redis' prefix.
 *
 * @param idempotencyLockTtlHours TTL in hours for idempotency lock keys
 */
@Validated
@ConfigurationProperties(prefix = "redis")
public record RedisProperties(
        long idempotencyLockTtlHours,
        CacheTTL cache
) {

    private static final Duration DEFAULT_SUMMARY_TTL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_SIZE_TTL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_GLOBAL_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_USER_TTL = Duration.ofMinutes(10);

    public RedisProperties {
        if (cache == null) {
            cache = new CacheTTL(null, null, null, null);
        }
    }

    public record CacheTTL(
            Duration lockerSummary,
            Duration lockerAvailableBySize,
            Duration defaultTtl,
            Duration userTtl
    ) {
        public CacheTTL {
            if (lockerSummary == null) lockerSummary = DEFAULT_SUMMARY_TTL;
            if (lockerAvailableBySize == null) lockerAvailableBySize = DEFAULT_SIZE_TTL;
            if (defaultTtl == null) defaultTtl = DEFAULT_GLOBAL_TTL;
            if (userTtl == null) userTtl = DEFAULT_USER_TTL;
        }
    }
}
