package com.smartlockr.support.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.support.application.service.SupportService;
import com.smartlockr.support.infrastructure.graphql.dto.TicketResponse;
import com.smartlockr.support.infrastructure.graphql.dto.UpdateTicketStatusInput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
@GraphQLController
@RequiredArgsConstructor
public class SupportAdminMutationResolver {

    private final SupportService supportService;

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse adminUpdateTicketStatus(@Argument @Valid UpdateTicketStatusInput input,
                                                  @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return supportService.updateTicketStatus(input, UUID.fromString(jwt.getSubject()));
    }
}
