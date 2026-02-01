package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.fleet.domain.enums.LockerSize;

import java.math.BigDecimal;

public record LockerSizeSummaryResponse(
        LockerSize size,
        BigDecimal hourlyRate,
        int availableCount
) {
}
