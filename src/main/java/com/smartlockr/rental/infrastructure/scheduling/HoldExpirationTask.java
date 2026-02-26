package com.smartlockr.rental.infrastructure.scheduling;

import com.smartlockr.rental.application.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled infrastructure adapter responsible for managing the expiration of
 * temporary holds and the penalisation of overdue active rentals.
 * * This component serves as a fallback mechanism for the primary Redis Keyspace
 * Notifications system. It ensures eventual consistency in the database by
 * catching events that might have been lost due to service downtime or network
 * partitions.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class HoldExpirationTask {

    private final RentalService rentalService;

    /**
     * Executes the reconciliation process at fixed intervals.
     * * Execution Strategy:
     * - fixedRate = 60000: Runs every 60 seconds (1 minute) to minimise
     * revenue leakage caused by lockers being falsely occupied.
     * - initialDelay = 10000: Defers the first execution by 10 seconds
     * after application startup to allow connection pools to warm up.
     * * The underlying database queries are backed by composite indexes, ensuring
     * that this frequent polling does not degrade relational database performance.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void executeReconciliation() {
        log.debug("Iniciando Job de limpieza de HOLDs y ACTIVEs expirados");
        try {
            int canceledHolds = rentalService.processExpiredHolds();
            if (canceledHolds > 0) {
                log.info("Se han cancelado {} HOLD(s) expirados.", canceledHolds);
            }

            int penalizedRentals = rentalService.processExpiredRentalsToPenalty();
            if (penalizedRentals > 0) {
                log.info("Se han aplicado {} penalizaciones nuevas.", penalizedRentals);
            }

        } catch (Exception e) {
            log.error("Fallo crítico durante el Job de Reconciliación.", e);
        }
    }
}
