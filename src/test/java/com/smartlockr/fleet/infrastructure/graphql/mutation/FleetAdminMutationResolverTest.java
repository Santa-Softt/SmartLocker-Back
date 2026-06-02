package com.smartlockr.fleet.infrastructure.graphql.mutation;

import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.RateInput;
import com.smartlockr.fleet.infrastructure.graphql.dto.UpdateBusinessConfigInput;
import com.smartlockr.fleet.infrastructure.graphql.dto.UpdateLockerStateInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
class FleetAdminMutationResolverTest {

    @Mock
    private FleetService fleetService;

    @Mock
    private BusinessService businessService;

    @InjectMocks
    private FleetAdminMutationResolver resolver;

    @Test
    @DisplayName("adminUpdateLockerState - delega en fleetService.updateLockerState")
    void shouldDelegateLockerStateUpdate() {
        UUID lockerId = UUID.randomUUID();
        var input = new UpdateLockerStateInput(lockerId, LockerState.MAINTENANCE);
        var response = new LockerResponse(lockerId, "L-1", LockerSize.M, LockerState.MAINTENANCE, BigDecimal.TEN);
        given(fleetService.updateLockerState(lockerId, LockerState.MAINTENANCE)).willReturn(response);

        var result = resolver.adminUpdateLockerState(input);

        assertThat(result).isEqualTo(response);
        then(fleetService).should().updateLockerState(lockerId, LockerState.MAINTENANCE);
    }

    @Test
    @DisplayName("adminUpdateBusinessConfig - convierte input a command y delega en businessService")
    void shouldDelegateBusinessConfigUpdate() {
        var rates = List.of(new RateInput(LockerSize.M, BigDecimal.TEN));
        var input = new UpdateBusinessConfigInput(
                300, 15, 1440, 10, 5, 5, ServiceStatus.OPERATIONAL, rates);
        var snapshot = new BusinessConfigSnapshot(
                UUID.randomUUID(), 300, 15, 1440, 10, 5, 5,
                ServiceStatus.OPERATIONAL, List.of());
        given(businessService.updateActiveBusinessConfig(ArgumentMatchers.any())).willReturn(snapshot);

        var result = resolver.adminUpdateBusinessConfig(input);

        assertThat(result).isEqualTo(snapshot);
        then(businessService).should().updateActiveBusinessConfig(ArgumentMatchers.any());
        verifyNoInteractions(fleetService);
    }
}
