package com.smartlockr.support.infrastructure.graphql.dto;

import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID userId,
        String subject,
        String description,
        UUID rentalId,
        TicketStatus status,
        TicketPriority priority,
        Instant createdAt,
        Instant updatedAt
) {
}
