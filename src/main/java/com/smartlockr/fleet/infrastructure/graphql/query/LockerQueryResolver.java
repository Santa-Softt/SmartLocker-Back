package com.smartlockr.fleet.infrastructure.graphql.query;

import com.smartlockr.commons.annotations.GraphQLController;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;

import java.util.List;

@GraphQLController
@RequiredArgsConstructor
public class LockerQueryResolver {
    private final FleetService fleetService;

    @QueryMapping
    public List<LockerResponse> getAvailableLockersBySize(@Argument LockerSize size) {
        return fleetService.findAvailableLockersBySize(size);
    }

    @QueryMapping
    public List<LockerSizeSummaryResponse> getLockerSizeSummaries() {
        return fleetService.getLockerSizeSummaries();
    }
}
