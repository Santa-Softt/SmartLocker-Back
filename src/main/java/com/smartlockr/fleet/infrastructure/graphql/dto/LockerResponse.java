package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;

import java.math.BigDecimal;
import java.util.UUID;

public record LockerResponse(
        UUID id,
        String label,
        LockerSize size,
        LockerState state,
        BigDecimal hourlyRate
) {}
