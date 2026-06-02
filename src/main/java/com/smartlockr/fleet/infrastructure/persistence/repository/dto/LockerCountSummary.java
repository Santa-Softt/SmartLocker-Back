package com.smartlockr.fleet.infrastructure.persistence.repository.dto;

import com.smartlockr.fleet.domain.enums.LockerSize;

public record LockerCountSummary(
        LockerSize size,
        long count
) {
}
