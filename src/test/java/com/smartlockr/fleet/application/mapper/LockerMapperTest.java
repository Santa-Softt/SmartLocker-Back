package com.smartlockr.fleet.application.mapper;

import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerUpdateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LockerMapperTest {

    private LockerMapper lockerMapper;

    @BeforeEach
    void setUp() {
        lockerMapper = Mappers.getMapper(LockerMapper.class);
    }

    @Test
    @DisplayName("toResponseList - mapea lista de Lockers a lista de LockerResponse")
    void shouldMapLockerList() {
        var l1 = new Locker(com.smartlockr.shared.utils.UuidV7.generate(), "L-1", LockerSize.M, LockerState.AVAILABLE, null);
        var l2 = new Locker(com.smartlockr.shared.utils.UuidV7.generate(), "L-2", LockerSize.L, LockerState.OCCUPIED, null);

        List<LockerResponse> result = lockerMapper.toResponseList(List.of(l1, l2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(l1.getId());
        assertThat(result.get(0).label()).isEqualTo("L-1");
        assertThat(result.get(0).state()).isEqualTo(LockerState.AVAILABLE);
        assertThat(result.get(1).size()).isEqualTo(LockerSize.L);
    }

    @Test
    @DisplayName("toResponse - mapea un Locker individual a LockerResponse con hourlyRate null")
    void shouldMapIndividualLockerWithNullHourlyRate() {
        var l = new Locker(com.smartlockr.shared.utils.UuidV7.generate(), "L-1", LockerSize.S, LockerState.MAINTENANCE, null);

        LockerResponse response = lockerMapper.toResponse(l);

        assertThat(response.id()).isEqualTo(l.getId());
        assertThat(response.label()).isEqualTo("L-1");
        assertThat(response.size()).isEqualTo(LockerSize.S);
        assertThat(response.state()).isEqualTo(LockerState.MAINTENANCE);
        assertThat(response.hourlyRate()).isNull();
    }

    @Test
    @DisplayName("toSummaryResponse - combina RateSnapshot y availableCount en LockerSizeSummaryResponse")
    void shouldMapRateSnapshotToSummary() {
        var rate = new RateSnapshot(LockerSize.XL, new BigDecimal("15.50"));

        LockerSizeSummaryResponse response = lockerMapper.toSummaryResponse(rate, 7);

        assertThat(response.size()).isEqualTo(LockerSize.XL);
        assertThat(response.hourlyRate()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(response.availableCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("toUpdateResponse - mapea LockerStateChangedEvent a LockerUpdateResponse")
    void shouldMapLockerEventToUpdateResponse() {
        UUID lockerId = com.smartlockr.shared.utils.UuidV7.generate();
        var event = new LockerStateChangedEvent(lockerId, LockerState.HOLD);

        LockerUpdateResponse response = lockerMapper.toUpdateResponse(event);

        assertThat(response.id()).isEqualTo(lockerId);
        assertThat(response.state()).isEqualTo(LockerState.HOLD);
    }
}
