package com.smartlockr.support.infrastructure.graphql.dto;

import com.smartlockr.support.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReportProblemInput(
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 2000) String description,
        UUID rentalId,
        TicketPriority priority
) {
}
