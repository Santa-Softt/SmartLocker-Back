package com.smartlockr.support.infrastructure.graphql.query;

import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SupportAdminQueryResolverTest {

    @Mock
    private SupportService supportService;

    @InjectMocks
    private SupportAdminQueryResolver resolver;

    @Test
    @DisplayName("adminGetTickets - delega en SupportService.findTicketsForAdmin")
    void shouldDelegateFindTickets() {
        var t1 = new TicketResponse(com.smartlockr.shared.utils.UuidV7.generate(), com.smartlockr.shared.utils.UuidV7.generate(), "S", "D", null,
                TicketStatus.OPEN, TicketPriority.MEDIUM, Instant.now(), Instant.now());
        given(supportService.findTicketsForAdmin(TicketStatus.OPEN)).willReturn(List.of(t1));

        var result = resolver.adminGetTickets(TicketStatus.OPEN);

        assertThat(result).containsExactly(t1);
        then(supportService).should().findTicketsForAdmin(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("adminGetTicket - delega en SupportService.findTicketForAdmin con el id")
    void shouldDelegateFindTicket() {
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        var t = new TicketResponse(ticketId, com.smartlockr.shared.utils.UuidV7.generate(), "S", "D", null,
                TicketStatus.OPEN, TicketPriority.MEDIUM, Instant.now(), Instant.now());
        given(supportService.findTicketForAdmin(ticketId)).willReturn(t);

        var result = resolver.adminGetTicket(ticketId);

        assertThat(result).isEqualTo(t);
        then(supportService).should().findTicketForAdmin(ticketId);
    }

    @Test
    @DisplayName("adminGetTicket - propaga EntityNotFoundException cuando el ticket no existe")
    void shouldPropagateEntityNotFound() {
        UUID ticketId = com.smartlockr.shared.utils.UuidV7.generate();
        given(supportService.findTicketForAdmin(ticketId))
                .willThrow(new jakarta.persistence.EntityNotFoundException("Ticket not found: " + ticketId));

        assertThatThrownBy(() -> resolver.adminGetTicket(ticketId))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
