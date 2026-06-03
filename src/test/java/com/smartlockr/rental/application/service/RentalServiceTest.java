package com.smartlockr.rental.application.service;

import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.billing.application.service.PricingService;
import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.rental.application.exception.IllegalLockerChangeStateException;
import com.smartlockr.rental.application.mapper.RentalMapper;
import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.rental.infrastructure.persistence.repository.RentalRepository;
import com.smartlockr.shared.infrastructure.redis.RedisHealthMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {

    @Mock
    private RentalRepository rentalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FleetService fleetService;
    @Mock
    private BusinessService businessService;
    @Mock
    private BillingService billingService;
    @Mock
    private PricingService pricingService;
    @Mock
    private RentalMapper rentalMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisHealthMonitor redisHealthMonitor;

    private RentalService rentalService;

    @BeforeEach
    void setUp() {
        rentalService = new RentalService(
                rentalRepository,
                userRepository,
                fleetService,
                businessService,
                billingService,
                pricingService,
                rentalMapper,
                redisTemplate,
                redisHealthMonitor
        );
    }

    @Test
    @DisplayName("findActiveRentalForUser - returns empty for null user")
    void shouldReturnEmptyActiveRentalForNullUser() {
        assertThat(rentalService.findActiveRentalForUser(null)).isEmpty();

        verifyNoInteractions(rentalRepository);
    }

    @Test
    @DisplayName("hasPenalizedRentalForUser - returns false for null user")
    void shouldReturnFalseForNullPenalizedUserLookup() {
        assertThat(rentalService.hasPenalizedRentalForUser(null)).isFalse();

        verifyNoInteractions(rentalRepository);
    }

    @Test
    @DisplayName("processExpiredHolds - cancels expired holds and releases lockers")
    void shouldCancelExpiredHoldsAndReleaseLockers() {
        Locker firstLocker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD);
        Locker secondLocker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD);
        Rental firstRental = rental(RentalState.HOLD, user(com.smartlockr.shared.utils.UuidV7.generate()), firstLocker);
        Rental secondRental = rental(RentalState.HOLD, user(com.smartlockr.shared.utils.UuidV7.generate()), secondLocker);

        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(rentalRepository.findAllByStateAndStartTimeBefore(org.mockito.ArgumentMatchers.eq(RentalState.HOLD), org.mockito.ArgumentMatchers.any(Instant.class)))
                .willReturn(List.of(firstRental, secondRental));

        int result = rentalService.processExpiredHolds();

        assertThat(result).isEqualTo(2);
        assertThat(firstRental.getState()).isEqualTo(RentalState.CANCELLED);
        assertThat(secondRental.getState()).isEqualTo(RentalState.CANCELLED);
        then(fleetService).should().releaseLockerFromHold(firstLocker.getId());
        then(fleetService).should().releaseLockerFromHold(secondLocker.getId());
    }

    @Test
    @DisplayName("processExpiredRentalsToPenalty - penalizes active rentals past end time")
    void shouldPenalizeExpiredActiveRentals() {
        Rental rental = rental(RentalState.ACTIVE, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));
        given(rentalRepository.findAllByStateAndEstimatedEndTimeBefore(org.mockito.ArgumentMatchers.eq(RentalState.ACTIVE), org.mockito.ArgumentMatchers.any(Instant.class)))
                .willReturn(List.of(rental));

        int result = rentalService.processExpiredRentalsToPenalty();

        assertThat(result).isEqualTo(1);
        assertThat(rental.getState()).isEqualTo(RentalState.PENALIZED);
        assertThat(rental.isPenalized()).isTrue();
    }

    @Test
    @DisplayName("releaseLocker - completes active rental and frees locker")
    void shouldReleaseActiveLocker() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED);
        Rental rental = rental(RentalState.ACTIVE, user(userId), locker);
        rental.setId(rentalId);

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(redisHealthMonitor.isRedisAvailable()).willReturn(false);

        var result = rentalService.releaseLocker(rentalId, userId);

        assertThat(result.message()).isEqualTo("Locker released successfully");
        assertThat(rental.getState()).isEqualTo(RentalState.COMPLETED);
        then(rentalRepository).should().save(rental);
        then(fleetService).should().updateLockerState(locker.getId(), LockerState.AVAILABLE);
        then(redisTemplate).should(never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("releaseLocker - rejects rentals from other users")
    void shouldRejectReleaseForDifferentUser() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.ACTIVE, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> rentalService.releaseLocker(rentalId, com.smartlockr.shared.utils.UuidV7.generate()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("releaseLocker - rejects non-active rentals")
    void shouldRejectReleaseForNonActiveRental() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.HOLD, user(userId), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> rentalService.releaseLocker(rentalId, userId))
                .isInstanceOf(IllegalLockerChangeStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("createExtensionPaymentOrder - validates duration and delegates to billing")
    void shouldCreateExtensionPaymentOrder() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.ACTIVE, user(userId), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));
        PaymentLinkResponse expected = new PaymentLinkResponse("https://pay.test");

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(pricingService.calculateTotalPrice(LockerSize.M, 60)).willReturn(BigDecimal.valueOf(100));
        given(billingService.createExtensionPaymentOrder(rentalId, userId, 60, BigDecimal.valueOf(100)))
                .willReturn(expected);

        PaymentLinkResponse result = rentalService.createExtensionPaymentOrder(rentalId, userId, 60);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("initiateHold - happy path: reserva locker, persiste rental, registra key en Redis")
    void shouldInitiateHoldWithRedisAvailable() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        User user = user(userId);
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.AVAILABLE);
        Rental savedRental = rental(RentalState.HOLD, user, locker);
        savedRental.setId(com.smartlockr.shared.utils.UuidV7.generate());

        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(fleetService.reserveLockerForHold(LockerSize.M)).willReturn(locker);
        given(pricingService.calculateTotalPrice(LockerSize.M, 60)).willReturn(BigDecimal.valueOf(150));
        given(rentalMapper.toNewHoldRental(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(savedRental);
        given(rentalRepository.save(savedRental)).willReturn(savedRental);
        given(rentalMapper.toActiveRentalResponse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse(
                        null, null, null, null, null, false, null));
        given(redisHealthMonitor.isRedisAvailable()).willReturn(true);

        var result = rentalService.initiateHold(LockerSize.M, userId.toString(), 60);

        assertThat(result).isNotNull();
        then(rentalRepository).should().save(savedRental);
        then(redisTemplate).should().opsForValue();
    }

    @Test
    @DisplayName("initiateHold - Redis no disponible omite el set del TTL")
    void shouldInitiateHoldWithoutRedisAvailability() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        User user = user(userId);
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.AVAILABLE);
        Rental savedRental = rental(RentalState.HOLD, user, locker);

        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(fleetService.reserveLockerForHold(LockerSize.M)).willReturn(locker);
        given(pricingService.calculateTotalPrice(LockerSize.M, 60)).willReturn(BigDecimal.valueOf(150));
        given(rentalMapper.toNewHoldRental(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(savedRental);
        given(rentalRepository.save(savedRental)).willReturn(savedRental);
        given(rentalMapper.toActiveRentalResponse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse(
                        null, null, null, null, null, false, null));
        given(redisHealthMonitor.isRedisAvailable()).willReturn(false);

        rentalService.initiateHold(LockerSize.M, userId.toString(), 60);

        then(redisTemplate).should(never()).opsForValue();
    }

    @Test
    @DisplayName("initiateHold - userId invalido lanza IllegalArgumentException")
    void shouldRejectInvalidUserIdOnInitiateHold() {
        assertThatThrownBy(() -> rentalService.initiateHold(LockerSize.M, "not-a-uuid", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    @DisplayName("initiateHold - userId vacio lanza IllegalArgumentException")
    void shouldRejectBlankUserIdOnInitiateHold() {
        assertThatThrownBy(() -> rentalService.initiateHold(LockerSize.M, "  ", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("initiateHold - duracion <= 0 lanza IllegalArgumentException")
    void shouldRejectNonPositiveDurationOnInitiateHold() {
        assertThatThrownBy(() -> rentalService.initiateHold(LockerSize.M, com.smartlockr.shared.utils.UuidV7.generate().toString(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
    }

    @Test
    @DisplayName("initiateHold - duracion fuera de rango lanza IllegalArgumentException")
    void shouldRejectOutOfRangeDurationOnInitiateHold() {
        given(businessService.getActiveBusinessConfig()).willReturn(config());

        assertThatThrownBy(() -> rentalService.initiateHold(LockerSize.M, com.smartlockr.shared.utils.UuidV7.generate().toString(), 9999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuera del rango");
    }

    @Test
    @DisplayName("initiateHold - usuario inexistente lanza UsernameNotFoundException")
    void shouldThrowWhenUserNotFoundOnInitiateHold() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.initiateHold(LockerSize.M, userId.toString(), 60))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("cancelUserHold - cancela rental en HOLD, libera locker y borra key de Redis")
    void shouldCancelHoldAndReleaseLocker() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD);
        Rental rental = rental(RentalState.HOLD, user(userId), locker);
        rental.setId(rentalId);

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(redisHealthMonitor.isRedisAvailable()).willReturn(true);

        var result = rentalService.cancelUserHold(rentalId, userId);

        assertThat(result.message()).isEqualTo("Locker released successfully");
        assertThat(rental.getState()).isEqualTo(RentalState.CANCELLED);
        then(rentalRepository).should().save(rental);
        then(fleetService).should().releaseLockerFromHold(locker.getId());
        then(redisTemplate).should().delete("hold:rental:" + rentalId);
    }

    @Test
    @DisplayName("cancelUserHold - rental no encontrado lanza IllegalArgumentException")
    void shouldThrowWhenRentalNotFoundOnCancel() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.cancelUserHold(rentalId, com.smartlockr.shared.utils.UuidV7.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rental not found");
    }

    @Test
    @DisplayName("cancelUserHold - rental no esta en HOLD lanza IllegalLockerChangeStateException")
    void shouldRejectCancelWhenNotInHold() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.ACTIVE, user(userId), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> rentalService.cancelUserHold(rentalId, userId))
                .isInstanceOf(IllegalLockerChangeStateException.class);
    }

    @Test
    @DisplayName("cancelUserHold - rental de otro usuario lanza AccessDeniedException")
    void shouldRejectCancelFromOtherUser() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.HOLD, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> rentalService.cancelUserHold(rentalId, com.smartlockr.shared.utils.UuidV7.generate()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("expireSystemHold - cancela rental y libera locker si esta en HOLD")
    void shouldExpireSystemHoldWhenInHoldState() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD);
        Rental rental = rental(RentalState.HOLD, user(com.smartlockr.shared.utils.UuidV7.generate()), locker);
        rental.setId(rentalId);

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        rentalService.expireSystemHold(rentalId);

        assertThat(rental.getState()).isEqualTo(RentalState.CANCELLED);
        then(rentalRepository).should().save(rental);
        then(fleetService).should().releaseLockerFromHold(locker.getId());
    }

    @Test
    @DisplayName("expireSystemHold - no hace nada si el rental no esta en HOLD")
    void shouldNotExpireSystemHoldWhenNotInHold() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.ACTIVE, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        rentalService.expireSystemHold(rentalId);

        then(rentalRepository).should(never()).save(rental);
        then(fleetService).should(never()).releaseLockerFromHold(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("applyPenaltyToRental - penaliza rental ACTIVE")
    void shouldApplyPenaltyToActiveRental() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.ACTIVE, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED));
        rental.setId(rentalId);

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        rentalService.applyPenaltyToRental(rentalId);

        assertThat(rental.getState()).isEqualTo(RentalState.PENALIZED);
        assertThat(rental.isPenalized()).isTrue();
        then(rentalRepository).should().save(rental);
    }

    @Test
    @DisplayName("applyPenaltyToRental - no hace nada si el rental no esta en ACTIVE")
    void shouldNotApplyPenaltyWhenNotActive() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();
        Rental rental = rental(RentalState.HOLD, user(com.smartlockr.shared.utils.UuidV7.generate()), locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.HOLD));

        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        rentalService.applyPenaltyToRental(rentalId);

        then(rentalRepository).should(never()).save(rental);
    }

    @Test
    @DisplayName("findActiveRentalForUser - devuelve snapshot cuando existe rental ACTIVE o PENALIZED")
    void shouldReturnActiveRentalSnapshotWhenExists() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        Locker locker = locker(com.smartlockr.shared.utils.UuidV7.generate(), LockerState.OCCUPIED);
        Rental rental = rental(RentalState.ACTIVE, user(userId), locker);
        rental.setId(com.smartlockr.shared.utils.UuidV7.generate());

        given(rentalRepository.findByUserIdAndStateIn(userId, List.of(RentalState.ACTIVE, RentalState.PENALIZED)))
                .willReturn(Optional.of(rental));

        var result = rentalService.findActiveRentalForUser(userId);

        assertThat(result).isPresent();
        assertThat(result.get().state()).isEqualTo(RentalState.ACTIVE);
        assertThat(result.get().lockerLabel()).isEqualTo("L-1");
    }

    @Test
    @DisplayName("findActiveRentalForUser - devuelve empty cuando userId es null")
    void shouldReturnEmptyWhenUserIdIsNull() {
        assertThat(rentalService.findActiveRentalForUser(null)).isEmpty();
    }

    @Test
    @DisplayName("hasPenalizedRentalForUser - devuelve true si existe rental PENALIZED")
    void shouldReturnTrueWhenPenalizedRentalExists() {
        UUID userId = com.smartlockr.shared.utils.UuidV7.generate();
        given(rentalRepository.existsByUserIdAndState(userId, RentalState.PENALIZED)).willReturn(true);

        assertThat(rentalService.hasPenalizedRentalForUser(userId)).isTrue();
    }

    @Test
    @DisplayName("processExpiredHolds - sin rentals expirados devuelve 0")
    void shouldReturnZeroWhenNoExpiredHolds() {
        given(businessService.getActiveBusinessConfig()).willReturn(config());
        given(rentalRepository.findAllByStateAndStartTimeBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());

        int result = rentalService.processExpiredHolds();

        assertThat(result).isZero();
        verifyNoInteractions(fleetService);
    }

    @Test
    @DisplayName("processExpiredRentalsToPenalty - sin rentals expirados devuelve 0")
    void shouldReturnZeroWhenNoExpiredActiveRentals() {
        given(rentalRepository.findAllByStateAndEstimatedEndTimeBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());

        int result = rentalService.processExpiredRentalsToPenalty();

        assertThat(result).isZero();
    }

    private BusinessConfigSnapshot config() {
        return new BusinessConfigSnapshot(
                com.smartlockr.shared.utils.UuidV7.generate(),
                300,
                15,
                1440,
                10,
                5,
                5,
                ServiceStatus.OPERATIONAL,
                List.of()
        );
    }

    private User user(UUID userId) {
        return User.builder()
                .id(userId)
                .fullName("Test User")
                .email("user@test.local")
                .role(Role.CONSUMER)
                .build();
    }

    private Locker locker(UUID lockerId, LockerState state) {
        return Locker.builder()
                .id(lockerId)
                .label("L-1")
                .size(LockerSize.M)
                .state(state)
                .build();
    }

    private Rental rental(RentalState state, User user, Locker locker) {
        return Rental.builder()
                .id(com.smartlockr.shared.utils.UuidV7.generate())
                .state(state)
                .startTime(Instant.now().minusSeconds(600))
                .estimatedEndTime(Instant.now().plusSeconds(600))
                .finalCost(BigDecimal.TEN)
                .user(user)
                .locker(locker)
                .build();
    }
}
