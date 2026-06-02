package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.UpdateBusinessConfigCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateBusinessConfigInput(
        @Min(240) int holdDurationSeconds,
        @Min(15) int minRentalDurationMinutes,
        @Min(1440) int maxRentalDurationMinutes,
        @Min(0) @Max(100) int penaltyPercentage,
        @Min(0) @Max(100) int streakDiscountPercentage,
        @Min(1) int streakThreshold,
        @NotNull ServiceStatus serviceStatus,
        @NotEmpty List<@Valid RateInput> rates
) {
    public UpdateBusinessConfigCommand toCommand() {
        return new UpdateBusinessConfigCommand(
                holdDurationSeconds,
                minRentalDurationMinutes,
                maxRentalDurationMinutes,
                penaltyPercentage,
                streakDiscountPercentage,
                streakThreshold,
                serviceStatus,
                rates.stream()
                        .map(RateInput::toRate)
                        .toList()
        );
    }
}
