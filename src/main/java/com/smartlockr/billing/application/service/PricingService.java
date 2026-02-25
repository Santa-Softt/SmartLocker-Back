package com.smartlockr.billing.application.service;

import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final BusinessService businessService;

    public BigDecimal calculateTotalPrice(LockerSize lockerSize, Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("La duración a facturar debe ser mayor a 0 minutos.");
        }

        BusinessConfig config = businessService.getActiveBusinessConfig();

        BigDecimal hourlyRate = config.getRates().stream()
                .filter(rate -> rate.getSize() == lockerSize)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tarifa no configurada para tamaño: " + lockerSize))
                .getHourlyRate();

        return hourlyRate.divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(durationMinutes))
                .setScale(2, RoundingMode.CEILING);
    }

}
