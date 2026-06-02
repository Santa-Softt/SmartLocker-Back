package com.smartlockr.fleet.infrastructure.dto;

import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;

import java.util.List;
import java.util.UUID;

/**
 * Immutable cache-safe representation of {@link BusinessConfig}.
 * Used for Redis serialization to avoid JPA proxy and lazy-loading issues.
 */
public record BusinessConfigSnapshot(
        UUID id,
        int holdDurationSeconds,
        int minRentalDurationMinutes,
        int maxRentalDurationMinutes,
        int penaltyPercentage,
        int streakThreshold,
        int streakDiscountPercentage,
        ServiceStatus serviceStatus,
        List<RateSnapshot> rates
) {}
