package com.smartlockr.fleet.infrastructure.graphql.mutation;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.UpdateBusinessConfigInput;
import com.smartlockr.fleet.infrastructure.graphql.dto.UpdateLockerStateInput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;

@Validated
@GraphQLController
@RequiredArgsConstructor
public class FleetAdminMutationResolver {

    private final FleetService fleetService;
    private final BusinessService businessService;

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public LockerResponse adminUpdateLockerState(@Argument @Valid UpdateLockerStateInput input) {
        return fleetService.updateLockerState(input.lockerId(), input.state());
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessConfigSnapshot adminUpdateBusinessConfig(@Argument @Valid UpdateBusinessConfigInput input) {
        return businessService.updateActiveBusinessConfig(input.toCommand());
    }
}
