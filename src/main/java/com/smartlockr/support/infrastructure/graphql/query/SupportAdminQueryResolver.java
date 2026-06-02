package com.smartlockr.support.infrastructure.graphql.query;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@GraphQLController
@RequiredArgsConstructor
public class SupportAdminQueryResolver {

    private final SupportService supportService;

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<TicketResponse> adminGetTickets(@Argument TicketStatus status) {
        return supportService.findTicketsForAdmin(status);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse adminGetTicket(@Argument UUID ticketId) {
        return supportService.findTicketForAdmin(ticketId);
    }
}
