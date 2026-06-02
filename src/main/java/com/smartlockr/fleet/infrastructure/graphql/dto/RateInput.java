package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RateInput(
        @NotNull LockerSize size,
        @NotNull @PositiveOrZero BigDecimal hourlyRate
) {
    public Rate toRate() {
        return new Rate(size, hourlyRate);
    }
}
