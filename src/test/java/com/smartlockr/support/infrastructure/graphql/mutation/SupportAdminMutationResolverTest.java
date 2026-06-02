package com.smartlockr.support.infrastructure.graphql.mutation;

import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import com.smartlockr.support.infrastructure.graphql.dto.UpdateTicketStatusInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SupportAdminMutationResolverTest {

    @Mock
    private SupportService supportService;

    @InjectMocks
    private SupportAdminMutationResolver resolver;

    @Test
    @DisplayName("adminUpdateTicketStatus - delega en SupportService con adminId del JWT")
    void shouldDelegateUpdateTicketStatus() {
        UUID adminId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = jwt(adminId);
        var input = new UpdateTicketStatusInput(ticketId, TicketStatus.CLOSED);
        var expected = new TicketResponse(ticketId, UUID.randomUUID(), "Subj", "Desc", null,
                TicketStatus.CLOSED, com.smartlockr.support.domain.enums.TicketPriority.MEDIUM,
                Instant.now(), Instant.now());
        given(supportService.updateTicketStatus(input, adminId)).willReturn(expected);

        var result = resolver.adminUpdateTicketStatus(input, jwt);

        assertThat(result).isEqualTo(expected);
        then(supportService).should().updateTicketStatus(input, adminId);
    }

    @Test
    @DisplayName("adminUpdateTicketStatus - jwt null lanza AccessDeniedException")
    void shouldThrowAccessDeniedWhenJwtIsNull() {
        var input = new UpdateTicketStatusInput(UUID.randomUUID(), TicketStatus.CLOSED);

        assertThatThrownBy(() -> resolver.adminUpdateTicketStatus(input, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sin iniciar sesión");

        then(supportService).should(never()).updateTicketStatus(any(), any());
    }

    private Jwt jwt(UUID userId) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", userId.toString())
        );
    }
}
