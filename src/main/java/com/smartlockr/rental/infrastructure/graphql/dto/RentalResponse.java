package com.smartlockr.rental.infrastructure.graphql.dto;

import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;

import java.time.Instant;
import java.util.UUID;

public record RentalResponse(
        UUID id,
        LockerResponse locker,
        Instant startTime,
        Instant estimatedEndTime,
        Instant holdExpiresAt,
        boolean isPenalized

) {
}
