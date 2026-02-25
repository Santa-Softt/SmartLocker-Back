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

@GraphQLController
@RequiredArgsConstructor
public class RentalMutationResolver {
    private final RentalService rentalService;

    @MutationMapping
    public RentalResponse initiateHold(@Argument LockerSize size,
                                       @Argument Integer durationMinutes,
                                       @AuthenticationPrincipal Jwt jwt){
        if(jwt == null){
            throw new AccessDeniedException("Usuario sin iniciar sesión");
        }
        return rentalService.initiateHold(size, jwt.getSubject(), durationMinutes);
    }

    @MutationMapping
    public RentalHoldResponse cancelLockerHold(@Argument UUID rentalId){
        return rentalService.cancelUserHold(rentalId);
    }
}
