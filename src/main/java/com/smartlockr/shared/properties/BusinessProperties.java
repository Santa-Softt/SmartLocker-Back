package com.smartlockr.shared.properties;

import com.smartlockr.fleet.domain.enums.LockerSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;


/**
 * Configuration properties for business rules and pricing.
 */
@Validated
@ConfigurationProperties(prefix = "smartlockr.business")
public record BusinessProperties(
        @Min(240) int maxHoldDurationSeconds,
        @Min(15) int minRentalDurationMinutes,
        @Min(1440) int maxRentalDurationMinutes,
        @Min(0) @Max(100) int penaltyPercentage,
        @Min(0) @Max(100) int streakDiscountPercentage,
        @Min(1) int streakThreshold,
        @NotEmpty Map<LockerSize, BigDecimal> rates,
        @NotEmpty Map<LockerSize, Integer> quantities
) {}