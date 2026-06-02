package com.smartlockr.fleet.infrastructure.graphql.query;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@GraphQLController
@RequiredArgsConstructor
public class FleetAdminQueryResolver {

    private final FleetService fleetService;
    private final BusinessService businessService;

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LockerResponse> adminGetLockers(@Argument LockerSize size, @Argument LockerState state) {
        return fleetService.findLockersForAdmin(size, state);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessConfigSnapshot adminGetBusinessConfig() {
        return businessService.getActiveBusinessConfig();
    }
}
