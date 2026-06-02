package com.smartlockr.billing.infrastructure.graphql.mutation;

import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import com.smartlockr.commons.annotations.GraphQLController;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * GraphQL mutation resolver for billing operations.
 * Handles payment order creation with proper authentication and ownership validation.
 */
@GraphQLController
@RequiredArgsConstructor
public class BillingMutationResolver {
    private final BillingService billingService;

    /**
     * Creates a payment order for an existing rental.
     * Validates that the rental belongs to the authenticated user.
     *
     * @param rentalId the UUID of the rental to create a payment order for
     * @param jwt the authenticated user's JWT token
     * @return a PaymentLinkResponse containing the payment link URL
     * @throws AccessDeniedException if the user is not authenticated or does not own the rental
     */
    @MutationMapping
    public PaymentLinkResponse createPaymentOrder(@Argument UUID rentalId,
                                                  @AuthenticationPrincipal Jwt jwt){
        if(jwt == null){
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return billingService.createPaymentOrder(rentalId, UUID.fromString(jwt.getSubject()));
    }

    @MutationMapping
    public PaymentLinkResponse createPenaltyPaymentOrder(@Argument UUID rentalId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return billingService.createPenaltyPaymentOrder(rentalId, UUID.fromString(jwt.getSubject()));
    }
}
