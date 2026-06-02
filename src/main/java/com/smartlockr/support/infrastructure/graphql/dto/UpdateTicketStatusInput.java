package com.smartlockr.support.infrastructure.graphql.dto;

import com.smartlockr.support.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateTicketStatusInput(
        @NotNull UUID ticketId,
        @NotNull TicketStatus status
) {
}
