package com.smartlockr.fleet.application.service;

import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerCountSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.*;

@DisplayName("FleetService")
@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private LockerMapper lockerMapper;

    @Mock
    private BusinessService businessService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FleetService fleetService;

    @Captor
    private ArgumentCaptor<LockerStateChangedEvent> eventCaptor;

    @BeforeEach
    void setUp() {
        fleetService = new FleetService(lockerRepository, lockerMapper, businessService, eventPublisher);
    }

    @Test
    @DisplayName("findAvailableLockersBySize - throws IllegalArgumentException when size is null")
    void shouldThrowIllegalArgumentExceptionWhenFindingAvailableLockersByNullSize() {
        // When & Then
        assertThatThrownBy(() -> fleetService.findAvailableLockersBySize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Locker size cannot be null.");

        verifyNoInteractions(lockerRepository, lockerMapper);
    }

    @Test
    @DisplayName("findAvailableLockersBySize - returns mapped LockerResponse list when lockers are found")
    void shouldReturnLockerResponsesWhenFindingAvailableLockersBySize() {
        // Given
        var size = LockerSize.M;
        var lockerId = UUID.randomUUID();
        var mockLockers = List.of(
                new Locker(lockerId, "L1", LockerSize.M, LockerState.AVAILABLE, null)
        );
        var expectedResponse = new LockerResponse(
                lockerId,
                "L1",
                LockerSize.M,
                LockerState.AVAILABLE,
                BigDecimal.TEN
        );
        var mockResponses = List.of(expectedResponse);

        given(lockerRepository.findBySizeAndStateOrderByLabelAsc(size, LockerState.AVAILABLE))
                .willReturn(mockLockers);
        given(lockerMapper.toResponseList(mockLockers))
                .willReturn(mockResponses);

        // When
        var result = fleetService.findAvailableLockersBySize(size);

        // Then
        assertThat(result).isEqualTo(mockResponses);
        then(lockerRepository).should().findBySizeAndStateOrderByLabelAsc(size, LockerState.AVAILABLE);
        then(lockerMapper).should().toResponseList(mockLockers);
    }

    @Test
    @DisplayName("reserveLockerForHold - reserves first available locker and transitions state to HOLD")
    void shouldReserveFirstAvailableLockerWhenReservingForHold() {
        // Given
        var size = LockerSize.L;
        var lockerId = UUID.randomUUID();
        var locker = new Locker(lockerId, "L1", size, LockerState.AVAILABLE, null);
        var limit = Limit.of(1);

        given(lockerRepository.findAndLockOne(size, LockerState.AVAILABLE, limit))
                .willReturn(Optional.of(locker));

        // When
        var result = fleetService.reserveLockerForHold(size);

        // Then
        assertThat(result).isEqualTo(locker);
        assertThat(result.getState()).isEqualTo(LockerState.HOLD);
        then(lockerRepository).should().save(locker);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .extracting(LockerStateChangedEvent::lockerId, LockerStateChangedEvent::newState)
                .containsExactly(lockerId, LockerState.HOLD);
    }

    @Test
    @DisplayName("reserveLockerForHold - throws UnavailableLockerException when no lockers are available")
    void shouldThrowUnavailableLockerExceptionWhenNoLockersAvailableForReservation() {
        // Given
        var size = LockerSize.S;

        given(lockerRepository.findAndLockOne(size, LockerState.AVAILABLE, Limit.of(1)))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> fleetService.reserveLockerForHold(size))
                .isInstanceOf(UnavailableLockerException.class)
                .hasMessage("No hay lockers disponibles para el tamaño solicitado.");

        verifyNoInteractions(lockerMapper, eventPublisher);
    }

    @Test
    @DisplayName("releaseLockerFromHold - releases locker and publishes AVAILABLE state event")
    void shouldReleaseLockerFromHoldToAvailable() {
        // Given
        var lockerId = UUID.randomUUID();

        given(lockerRepository.releaseLockerFromHold(lockerId, LockerState.HOLD, LockerState.AVAILABLE))
                .willReturn(1);

        // When
        fleetService.releaseLockerFromHold(lockerId);

        // Then
        then(lockerRepository).should().releaseLockerFromHold(lockerId, LockerState.HOLD, LockerState.AVAILABLE);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .extracting(LockerStateChangedEvent::lockerId, LockerStateChangedEvent::newState)
                .containsExactly(lockerId, LockerState.AVAILABLE);
    }

    @Test
    @DisplayName("releaseLockerFromHold - does not publish event when locker is not in HOLD state")
    void shouldNotReleaseLockerIfStateIsNotHold() {
        // Given
        var lockerId = UUID.randomUUID();

        given(lockerRepository.releaseLockerFromHold(lockerId, LockerState.HOLD, LockerState.AVAILABLE))
                .willReturn(0);

        // When
        fleetService.releaseLockerFromHold(lockerId);

        // Then
        then(lockerRepository).should().releaseLockerFromHold(lockerId, LockerState.HOLD, LockerState.AVAILABLE);
        then(eventPublisher).should(times(0)).publishEvent(any(LockerStateChangedEvent.class));
    }

    @Test
    @DisplayName("releaseLockerFromHold - handles null locker ID without throwing")
    void shouldHandleNullLockerIdGracefullyInReleaseOperation() {
        // When
        fleetService.releaseLockerFromHold(null);

        // Then
        verifyNoInteractions(lockerRepository, eventPublisher);
    }

    @Test
    @DisplayName("releaseLockerFromHold - does not publish event when locker ID does not exist")
    void shouldNotPublishEventWhenReleasingNonExistentLocker() {
        // Given
        var nonExistentId = UUID.randomUUID();

        given(lockerRepository.releaseLockerFromHold(nonExistentId, LockerState.HOLD, LockerState.AVAILABLE))
                .willReturn(0);

        // When
        fleetService.releaseLockerFromHold(nonExistentId);

        // Then
        then(lockerRepository).should().releaseLockerFromHold(nonExistentId, LockerState.HOLD, LockerState.AVAILABLE);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("getLockerSizeSummaries - returns summaries with correct availability counts and rates per size")
    void shouldReturnLockerSizeSummariesCorrectly() {
        // Given
        var rateM = new RateSnapshot(LockerSize.M, BigDecimal.TEN);
        var rateL = new RateSnapshot(LockerSize.L, BigDecimal.valueOf(15));
        var rates = List.of(rateM, rateL);

        var countSummaryM = new LockerCountSummary(LockerSize.M, 5L);
        var countSummaryL = new LockerCountSummary(LockerSize.L, 8L);
        var counts = List.of(countSummaryM, countSummaryL);

        var mockSummaryM = new LockerSizeSummaryResponse(LockerSize.M, BigDecimal.TEN, 5);
        var mockSummaryL = new LockerSizeSummaryResponse(LockerSize.L, BigDecimal.valueOf(15), 8);
        var expectedSummaries = List.of(mockSummaryM, mockSummaryL);

        var mockConfig = new BusinessConfigSnapshot(
                UUID.randomUUID(),
                300,
                5,
                1440,
                10,
                5,
                5,
                ServiceStatus.OPERATIONAL,
                rates
        );

        given(businessService.getActiveBusinessConfig()).willReturn(mockConfig);
        given(lockerRepository.countByStateGroupedBySize(LockerState.AVAILABLE)).willReturn(counts);
        given(lockerMapper.toSummaryResponse(rateM, 5)).willReturn(mockSummaryM);
        given(lockerMapper.toSummaryResponse(rateL, 8)).willReturn(mockSummaryL);

        // When
        var result = fleetService.getLockerSizeSummaries();

        // Then
        assertThat(result).isEqualTo(expectedSummaries);
        then(businessService).should().getActiveBusinessConfig();
        then(lockerRepository).should().countByStateGroupedBySize(LockerState.AVAILABLE);
        then(lockerMapper).should().toSummaryResponse(eq(rateM), eq(5));
        then(lockerMapper).should().toSummaryResponse(eq(rateL), eq(8));
    }

}