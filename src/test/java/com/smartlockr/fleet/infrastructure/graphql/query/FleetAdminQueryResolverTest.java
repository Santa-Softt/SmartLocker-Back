package com.smartlockr.fleet.infrastructure.graphql.query;

import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FleetAdminQueryResolverTest {

    @Mock
    private FleetService fleetService;

    @Mock
    private BusinessService businessService;

    @InjectMocks
    private FleetAdminQueryResolver resolver;

    @Test
    @DisplayName("adminGetLockers - delega en fleetService.findLockersForAdmin con los argumentos")
    void shouldDelegateFindLockersToFleetService() {
        var size = LockerSize.M;
        var state = LockerState.AVAILABLE;
        var lockers = List.of(new LockerResponse(UUID.randomUUID(), "L-1", size, state, BigDecimal.TEN));
        given(fleetService.findLockersForAdmin(size, state)).willReturn(lockers);

        var result = resolver.adminGetLockers(size, state);

        assertThat(result).isEqualTo(lockers);
        then(fleetService).should().findLockersForAdmin(size, state);
    }

    @Test
    @DisplayName("adminGetBusinessConfig - delega en businessService.getActiveBusinessConfig")
    void shouldReturnActiveBusinessConfig() {
        var snapshot = new BusinessConfigSnapshot(
                UUID.randomUUID(), 300, 15, 1440, 10, 5, 5,
                ServiceStatus.OPERATIONAL, List.of());
        given(businessService.getActiveBusinessConfig()).willReturn(snapshot);

        var result = resolver.adminGetBusinessConfig();

        assertThat(result).isEqualTo(snapshot);
        verifyNoInteractions(fleetService);
    }
}
