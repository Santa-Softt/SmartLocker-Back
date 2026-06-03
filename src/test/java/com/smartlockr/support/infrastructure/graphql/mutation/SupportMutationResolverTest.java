package com.smartlockr.support.infrastructure.graphql.mutation;

import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.domain.enums.TicketPriority;
import com.smartlockr.support.domain.enums.TicketStatus;
import com.smartlockr.support.infrastructure.graphql.dto.ReportProblemInput;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SupportMutationResolverTest {

    @Mock
    private SupportService supportService;

    @InjectMocks
    private SupportMutationResolver resolver;

    @Test
    @DisplayName("reportProblem - delega en SupportService con el userId del JWT")
    void shouldDelegateReportProblem() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Jwt jwt = jwt(userId);
        var input = new ReportProblemInput("Subj", "Desc", rentalId, TicketPriority.HIGH);
        var expected = new TicketResponse(com.smartlockr.shared.utils.UuidV7.generate(), userId, "Subj", "Desc", rentalId,
                TicketStatus.OPEN, TicketPriority.HIGH, Instant.now(), Instant.now());
        given(supportService.reportProblem(input, userId)).willReturn(expected);

        var result = resolver.reportProblem(input, jwt);

        assertThat(result).isEqualTo(expected);
        then(supportService).should().reportProblem(input, userId);
    }

    @Test
    @DisplayName("reportProblem - jwt null lanza AccessDeniedException")
    void shouldThrowAccessDeniedWhenJwtIsNull() {
        var input = new ReportProblemInput("Subj", "Desc", com.smartlockr.shared.utils.UuidV7.generate(), TicketPriority.LOW);

        assertThatThrownBy(() -> resolver.reportProblem(input, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sin iniciar sesión");

        then(supportService).should(never()).reportProblem(any(), any());
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
