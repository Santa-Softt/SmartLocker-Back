package com.smartlockr.shared.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors Redis connectivity and logs state changes.
 * Used by reconciliation scheduler to adjust polling interval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthMonitor {

    private final RedisConnectionFactory redisConnectionFactory;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);

    /**
     * Checks Redis connectivity every 10 seconds.
     * Logs only on state change to avoid noise.
     */
    @Scheduled(fixedRate = 10000)
    public void checkHealth() {
        try {
            redisConnectionFactory.getConnection().ping();
            redisAvailable.set(true);
        } catch (Exception _) {
            if (redisAvailable.compareAndSet(true, false)) {
                log.warn("Redis connection lost. Reconciliation will run in aggressive mode.");
            }
        }
    }

    /**
     * Returns current Redis availability status.
     */
    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    /**
     * Returns reconciliation interval: 5000ms if Redis is up, 2000ms if down.
     * Used by HoldExpirationTask via SpEL expression.
     */
    @SuppressWarnings("unused")
    public long getReconciliationInterval() {
        return isRedisAvailable() ? 5000 : 2000;
    }
}
