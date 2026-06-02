package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.fleet.domain.enums.LockerState;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateLockerStateInput(
        @NotNull UUID lockerId,
        @NotNull LockerState state
) {
}
