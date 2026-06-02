package com.smartlockr.support.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.infrastructure.graphql.dto.ReportProblemInput;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
@GraphQLController
@RequiredArgsConstructor
public class SupportMutationResolver {

    private final SupportService supportService;

    @MutationMapping
    public TicketResponse reportProblem(@Argument @Valid ReportProblemInput input,
                                        @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return supportService.reportProblem(input, UUID.fromString(jwt.getSubject()));
    }
}
