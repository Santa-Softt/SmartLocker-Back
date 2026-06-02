package com.smartlockr.shared.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Monitors Redis connectivity and maintains the current availability state.
 * Prevents connection leaks by using try-with-resources.
 * State changes are logged to provide observability over the infrastructure health.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthMonitor {

    private final RedisConnectionFactory redisConnectionFactory;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);

    /**
     * Verifies Redis connectivity periodically.
     * Ensures the connection is properly closed to prevent connection pool exhaustion.
     * Logs state transitions between available and unavailable.
     */
    @Scheduled(fixedRate = 10000)
    public void checkHealth() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            if (redisAvailable.compareAndSet(false, true)) {
                log.info("Redis connection recovered. Resuming standard operations.");
            }
        } catch (Exception _) {
            if (redisAvailable.compareAndSet(true, false)) {
                log.warn("Redis connection lost. Reconciliation will run in aggressive mode.");
            }
        }
    }

    /**
     * Retrieves the current Redis availability status.
     *
     * @return true if Redis is reachable, false otherwise
     */
    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    /**
     * Calculates the target reconciliation interval based on the current infrastructure state.
     *
     * @return interval in milliseconds
     */
    @SuppressWarnings("unused")
    public long getReconciliationInterval() {
        return isRedisAvailable() ? 5000L : 2000L;
    }
}
