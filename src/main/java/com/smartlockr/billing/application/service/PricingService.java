package com.smartlockr.billing.application.service;

import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final BusinessService businessService;

    /**
     * Calculates the total rental price for a given locker size and duration.
     * Range validation is delegated to the caller — this method only guarantees
     * the duration is a positive number.
     *
     * @param lockerSize the size of the locker being rented
     * @param durationMinutes the rental duration in minutes, must be greater than 0
     * @return the total price rounded up to 2 decimal places
     * @throws IllegalArgumentException if the duration is zero or negative
     * @throws IllegalStateException if no rate is configured for the given locker size
     */
    public BigDecimal calculateTotalPrice(LockerSize lockerSize, int durationMinutes) {
        validateDuration(durationMinutes);

        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();
        BigDecimal hourlyRate = resolveHourlyRate(config, lockerSize);

        return hourlyRate
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(durationMinutes))
                .setScale(2, RoundingMode.CEILING);
    }

    /**
     * Resolves the hourly rate for the given locker size from the configuration snapshot.
     *
     * @param config the active business configuration snapshot
     * @param lockerSize the locker size to look up
     * @return the hourly rate for the given size
     * @throws IllegalStateException if no rates are defined or no rate matches the given size
     */
    private BigDecimal resolveHourlyRate(BusinessConfigSnapshot config, LockerSize lockerSize) {
        List<RateSnapshot> rates = config.rates();
        if (rates == null || rates.isEmpty()) {
            throw new IllegalStateException(
                    "La configuración de negocio no tiene tarifas definidas.");
        }
        return rates.stream()
                .filter(rate -> rate.size() == lockerSize)
                .findFirst()
                .map(RateSnapshot::hourlyRate)
                .orElseThrow(() -> new IllegalStateException(
                        "Tarifa no configurada para tamaño: " + lockerSize));
    }

    /**
     * Validates that the duration is a positive number.
     * Range bounds are enforced upstream by the caller using the active business configuration.
     *
     * @param durationMinutes the duration to validate in minutes
     * @throws IllegalArgumentException if the duration is zero or negative
     */
    private void validateDuration(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "La duración a facturar debe ser mayor a 0 minutos.");
        }
    }

}
