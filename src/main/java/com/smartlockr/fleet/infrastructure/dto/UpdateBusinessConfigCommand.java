package com.smartlockr.fleet.infrastructure.dto;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Command carrying the updated values for a {@link BusinessConfig} mutation.
 * Used by {@link BusinessService#updateBusinessConfig(UpdateBusinessConfigCommand)}.
 */
public record UpdateBusinessConfigCommand(
        @Min(240) int holdDurationSeconds,
        @Min(15) int minRentalDurationMinutes,
        @Min(1440) int maxRentalDurationMinutes,
        @Min(0) @Max(100) int penaltyPercentage,
        @Min(0) @Max(100) int streakDiscountPercentage,
        @Min(1) int streakThreshold,
        @NotNull ServiceStatus serviceStatus,
        @NotEmpty List<Rate> rates
) {}
