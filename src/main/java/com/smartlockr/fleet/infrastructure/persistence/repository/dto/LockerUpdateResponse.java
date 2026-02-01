package com.smartlockr.fleet.infrastructure.persistence.repository.dto;

import com.smartlockr.fleet.domain.enums.LockerState;

import java.util.UUID;

public record LockerUpdateResponse(UUID id, LockerState state) {}
