package com.smartlockr.rental.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.rental.application.service.RentalService;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalHoldResponse;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * GraphQL mutation resolver for rental operations.
 * Handles hold initiation and cancellation with proper authentication.
 */
@GraphQLController
@RequiredArgsConstructor
public class RentalMutationResolver {
    private final RentalService rentalService;

    /**
     * Initiates a hold on an available locker for the authenticated user.
     *
     * @param size the requested locker size
     * @param durationMinutes the desired rental duration in minutes
     * @param jwt the authenticated user's JWT token
     * @return a RentalResponse containing rental details and hold expiration time
     * @throws AccessDeniedException if the user is not authenticated
     */
    @MutationMapping
    public RentalResponse initiateHold(@Argument LockerSize size,
                                       @Argument Integer durationMinutes,
                                       @AuthenticationPrincipal Jwt jwt){
        if(jwt == null){
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return rentalService.initiateHold(size, jwt.getSubject(), durationMinutes);
    }

    /**
     * Cancels an active hold initiated by the authenticated user.
     * Validates that the rental belongs to the requesting user.
     *
     * @param rentalId the UUID of the rental to cancel
     * @param jwt the authenticated user's JWT token
     * @return a RentalHoldResponse confirming the cancellation
     * @throws AccessDeniedException if the user is not authenticated or does not own the rental
     */
    @MutationMapping
    public RentalHoldResponse cancelLockerHold(@Argument UUID rentalId,
                                               @AuthenticationPrincipal Jwt jwt){
        if(jwt == null){
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return rentalService.cancelUserHold(rentalId, UUID.fromString(jwt.getSubject()));
    }
}
