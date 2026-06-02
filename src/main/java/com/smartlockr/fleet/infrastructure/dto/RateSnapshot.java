package com.smartlockr.fleet.infrastructure.dto;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerSize;

import java.math.BigDecimal;

/**
 * Immutable cache-safe representation of a {@link Rate}.
 */
public record RateSnapshot(
        LockerSize size,
        BigDecimal hourlyRate
) {}
