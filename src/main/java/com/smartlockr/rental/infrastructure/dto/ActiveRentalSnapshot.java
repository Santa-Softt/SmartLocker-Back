package com.smartlockr.rental.infrastructure.dto;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.rental.domain.enums.RentalState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ActiveRentalSnapshot(
        UUID rentalId,
        RentalState state,
        UUID lockerId,
        String lockerLabel,
        LockerSize lockerSize,
        LockerState lockerState,
        Instant startTime,
        Instant estimatedEndTime,
        BigDecimal finalCost,
        boolean penalized
) {
}
