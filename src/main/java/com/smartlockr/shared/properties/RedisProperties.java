package com.smartlockr.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis configuration properties.
 * Maps values defined in application.yml under the 'redis' prefix.
 *
 * @param idempotencyLockTtlHours TTL in hours for idempotency lock keys
 */
@Validated
@ConfigurationProperties(prefix = "redis")
public record RedisProperties(
        long idempotencyLockTtlHours
) {}
