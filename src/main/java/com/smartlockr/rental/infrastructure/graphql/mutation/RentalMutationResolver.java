package com.smartlockr.rental.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.rental.application.service.RentalService;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@GraphQLController
@RequiredArgsConstructor
public class RentalMutationResolver {
    private final RentalService rentalService;

    @MutationMapping
    public RentalResponse initiateHold(@Argument LockerSize size, @AuthenticationPrincipal Jwt jwt){
        return rentalService.initiateHold(size, jwt.getSubject());
    }
}
