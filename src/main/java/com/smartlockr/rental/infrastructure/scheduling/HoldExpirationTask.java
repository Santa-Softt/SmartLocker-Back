package com.smartlockr.rental.infrastructure.scheduling;

import com.smartlockr.rental.application.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada para gestionar la expiración de las reservas temporales (HOLDs).
 * Este componente actúa como un adaptador de infraestructura que invoca la lógica
 * de negocio en el servicio de aplicación a intervalos fijos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpirationTask {
    private final RentalService rentalService;

    /**
     * Disparador programado que invoca el proceso de negocio para limpiar los HOLDs expirados.
     */
    @Scheduled(cron = "0 * * * * *")
    public void triggerExpiredHoldsCleanup() {
        log.info("Triggering scheduled task: Clean up expired HOLDs...");
        try {
            int cleanedCount = rentalService.processExpiredHolds();
            if (cleanedCount > 0) log.info("Task finished: Processed {} expired HOLD(s).", cleanedCount);
            log.info("Task finished: No expired HOLDs found or processed.");
        } catch (Exception e) {
            log.error("Error during scheduled task 'triggerExpiredHoldsCleanup'", e);
        }
    }
}
