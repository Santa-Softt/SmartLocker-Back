package com.smartlockr.shared.properties;

import com.smartlockr.fleet.domain.enums.LockerSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;

/*
 * Configuración de reglas de negocio y tarifas para SmartLockr.
 */
@Validated
@ConfigurationProperties(prefix = "smartlockr.business")
public record BusinessProperties(
        /*
         * Tiempo máximo retención locker antes de finalizar alquiler.
         * Unidad: Segundos. Mínimo: 120.
         */
        @Min(240) int maxHoldDurationSeconds,

        /*
         * Duración mínima alquiler.
         * Unidad: Minutos. Mínimo: 5.
         */
        @Min(5) int minRentalDuration,

        /*
         * Duración máxima alquiler.
         * Unidad: Minutos. Mínimo: 1440 (24h).
         */
        @Min(1440) int maxRentalDuration,

        /*
         * Porcentaje penalización (0-100).
         */
        @Min(0) @Max(100) int penaltyPercentage,

        /*
         * Porcentaje descuento por racha (0-100).
         */
        @Min(0) @Max(100) int streakDiscountPercentage,

        /*
         * Alquileres consecutivos para activar descuento por racha.
         */
        @Min(1) int streakThreshold,

        /*
         * Tarifas por tamaño de locker.
         * Claves esperadas: XS, S, M, L, XL.
         * Valor: > 0.
         */
        @NotEmpty Map<LockerSize, BigDecimal> rates,

        /*
         * Cantidad de lockers disponibles por tamaño.
         * Claves esperadas: XS, S, M, L, XL.
         * Valor: >= 1.
         */
        @NotEmpty Map<LockerSize, Integer> quantities
) {
}
