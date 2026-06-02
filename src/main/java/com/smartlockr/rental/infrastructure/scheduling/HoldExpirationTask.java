package com.smartlockr.rental.infrastructure.scheduling;

import com.smartlockr.rental.application.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled infrastructure adapter responsible for managing the expiration of
 * temporary holds and the penalization of overdue active rentals.
 * This component serves as the primary mechanism for expiration detection,
 * using database-backed polling with dynamic intervals:
 * - 5 seconds when Redis is operational
 * - 2 seconds when Redis is unavailable (aggressive fallback)
 * The database is the source of truth for rental expiration times.
 * Redis keyspace events are used as an optimization when available.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smartlockr.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class HoldExpirationTask {

    private final RentalService rentalService;

    /**
     * Executes the reconciliation process at dynamic intervals based on Redis health.
     * Execution Strategy:
     * - fixedRateString: Uses dynamic interval from RedisHealthMonitor
     *   - 5 seconds if Redis is operational (normal mode)
     *   - 2 seconds if Redis is down (aggressive fallback mode)
     * - initialDelay = 5000: Defers the first execution by 5 seconds
     * after application startup to allow connection pools to warm up.
     * The underlying database queries are backed by composite indexes, ensuring
     * that this frequent polling does not degrade relational database performance.
     * Expected latency:
     * - Maximum 5 seconds for expiration detection (Redis UP)
     * - Maximum 2 seconds for expiration detection (Redis DOWN)
     */
    @Scheduled(fixedRateString = "#{@redisHealthMonitor.reconciliationInterval}", initialDelay = 5000)
    public void executeReconciliation() {
        long startTime = System.currentTimeMillis();

        try {
            int canceledHolds = rentalService.processExpiredHolds();
            int penalizedRentals = rentalService.processExpiredRentalsToPenalty();

            long executionTime = System.currentTimeMillis() - startTime;

            if (canceledHolds > 0 || penalizedRentals > 0) {
                log.info("⏱️ Reconciliation completed in {}ms: {} holds canceled, {} penalties applied.", 
                         executionTime, canceledHolds, penalizedRentals);
            }

            if (executionTime > 1000) {
                log.warn("⚠️ Reconciliation took {}ms (threshold: 1000ms). Consider checking database performance.", executionTime);
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ Critical failure during Reconciliation Job (execution time: {}ms).", executionTime, e);
        }
    }
}
