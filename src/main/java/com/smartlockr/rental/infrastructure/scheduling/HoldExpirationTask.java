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
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpirationTask {

    private final RentalService rentalService;

    /**
     * fixedRate: Ejecución cada 30 minutos.
     * initialDelay: Espera 10 segundos (10000ms) tras el arranque antes de la primera ejecución.
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 10000)
    public void executeReconciliation() {
        log.debug("Iniciando Job de Reconciliación de Alquileres...");

        try {
            int canceledHolds = rentalService.processExpiredHolds();
            if (canceledHolds > 0) log.info("Se han cancelado {} HOLD(s) expirados.", canceledHolds);

            int penalizedRentals = rentalService.processExpiredRentalsToPenalty();
            if (penalizedRentals > 0) log.info("Se han aplicado {} penalizaciones nuevas.", penalizedRentals);

        } catch (Exception e) {
            log.error("Fallo crítico durante el Job de Reconciliación.", e);
        }
    }
}
