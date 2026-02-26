package com.smartlockr.rental.application.service;

import com.smartlockr.billing.application.service.PricingService;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.rental.application.exception.IllegalLockerChangeStateException;
import com.smartlockr.rental.application.mapper.RentalMapper;
import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalHoldResponse;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.rental.infrastructure.persistence.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final FleetService fleetService;
    private final BusinessService businessService;
    private final PricingService pricingService;
    private final RentalMapper rentalMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String HOLD_KEY_PREFIX = "hold:rental:";

    /**
     * Initiates a hold on an available locker of the requested size for the given user.
     * Creates a pending rental, calculates the final price, and registers a Redis TTL key
     * to handle hold expiration automatically.
     *
     * @param size the requested locker size
     * @param userId the UUID of the requesting user as a string
     * @param durationMinutes the desired rental duration in minutes
     * @return a {@link RentalResponse} containing rental details and hold expiration time
     * @throws IllegalArgumentException if the user ID is invalid or the duration is out of bounds
     * @throws UsernameNotFoundException if no user is found for the given ID
     */
    @Transactional
    public RentalResponse initiateHold(LockerSize size, String userId, int durationMinutes) {
        validateUserId(userId);

        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();

        verifyRentalDuration(durationMinutes, config);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        Locker lockedLocker = fleetService.reserveLockerForHold(size);

        Instant now = Instant.now();
        Instant holdExpiration = now.plusSeconds(config.holdDurationSeconds());
        Instant serviceExpiration = holdExpiration.plus(durationMinutes, ChronoUnit.MINUTES);

        BigDecimal finalPrice = pricingService.calculateTotalPrice(size, durationMinutes);

        Rental rental = rentalMapper.toNewHoldRental(
                user,
                lockedLocker,
                now,
                serviceExpiration,
                finalPrice
        );

        Rental savedRental = rentalRepository.save(rental);

        String redisKey = HOLD_KEY_PREFIX + savedRental.getId().toString();
        redisTemplate.opsForValue().set(redisKey, "PENDING", config.holdDurationSeconds(), TimeUnit.SECONDS);
        log.info("Clave transaccional generada en Redis: {} con TTL de {} segundos", redisKey, config.holdDurationSeconds());

        return rentalMapper.toActiveRentalResponse(savedRental, holdExpiration);
    }

    /**
     * Cancels an active hold initiated by the user.
     * Releases the locker and removes the Redis TTL key.
     *
     * @param rentalId the UUID of the rental to cancel
     * @return a {@link RentalHoldResponse} confirming the cancellation
     * @throws IllegalArgumentException if no rental is found for the given ID
     * @throws IllegalLockerChangeStateException if the rental is not in HOLD state
     */
    @Transactional
    public RentalHoldResponse cancelUserHold(UUID rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Alquiler no encontrado para ID: " + rentalId));

        if (rental.getState() != RentalState.HOLD) {
            throw new IllegalLockerChangeStateException(
                    "Solo se pueden cancelar operaciones en estado de retención (HOLD).");
        }

        rental.setState(RentalState.CANCELLED);
        fleetService.releaseLockerFromHold(rental.getLocker().getId());
        rentalRepository.save(rental);

        redisTemplate.delete(HOLD_KEY_PREFIX + rentalId);
        log.info("Hold cancelado por el usuario. Clave de Redis eliminada preventivamente para: {}", rentalId);

        return new RentalHoldResponse("Locker liberado exitosamente");
    }

    /**
     * Triggered when Redis notifies that the hold TTL has expired.
     * Cancels the rental and releases the locker if it is still in HOLD state.
     *
     * @param rentalId the UUID of the rental in HOLD state
     */
    @Transactional
    public void expireSystemHold(UUID rentalId) {
        rentalRepository.findById(rentalId).ifPresent(rental -> {
            if (rental.getState() == RentalState.HOLD) {
                log.info("Redis: Expirando HOLD para Rental {}", rentalId);
                rental.setState(RentalState.CANCELLED);
                rentalRepository.save(rental);
                fleetService.releaseLockerFromHold(rental.getLocker().getId());
            }
        });
    }

    /**
     * Reconciliation job fallback that cancels any rentals still in HOLD state
     * beyond the configured hold duration. Handles cases where Redis expiration
     * events may have been missed.
     *
     * @return the number of rentals expired and cancelled
     */
    @Transactional
    public int processExpiredHolds() {
        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();
        Instant expirationThreshold = Instant.now().minusSeconds(config.holdDurationSeconds());

        List<Rental> expiredRentals = rentalRepository.findAllByStateAndStartTimeBefore(
                RentalState.HOLD, expirationThreshold);

        if (expiredRentals.isEmpty()) {
            return 0;
        }

        for (Rental rental : expiredRentals) {
            rental.setState(RentalState.CANCELLED);
            fleetService.releaseLockerFromHold(rental.getLocker().getId());
        }

        log.info("Reconciliación: {} holds expirados cancelados.", expiredRentals.size());
        return expiredRentals.size();
    }

    /**
     * Triggered when Redis notifies that the active rental TTL has expired.
     * Transitions the rental to PENALIZED state to block further actions and mark outstanding debt.
     *
     * @param rentalId the UUID of the active rental
     */
    @Transactional
    public void applyPenaltyToRental(UUID rentalId) {
        rentalRepository.findById(rentalId).ifPresent(rental -> {
            if (rental.getState() == RentalState.ACTIVE) {
                log.info("Redis: Aplicando penalización a Rental {}", rentalId);
                rental.setState(RentalState.PENALIZED);
                rental.setPenalized(true);
                rentalRepository.save(rental);
            }
        });
    }

    /**
     * Reconciliation job fallback that penalizes any rentals still in ACTIVE state
     * past their estimated end time. Handles cases where Redis expiration events
     * may have been missed.
     *
     * @return the number of rentals transitioned to PENALIZED state
     */
    @Transactional
    public int processExpiredRentalsToPenalty() {
        List<Rental> expiredRentals = rentalRepository.findAllByStateAndEstimatedEndTimeBefore(
                RentalState.ACTIVE, Instant.now());

        if (expiredRentals.isEmpty()) {
            return 0;
        }

        for (Rental rental : expiredRentals) {
            rental.setState(RentalState.PENALIZED);
            rental.setPenalized(true);
            log.info("Alquiler finalizado por job de reconciliación, aplicando PENALIDAD: {}", rental.getId());
        }

        log.info("Reconciliación: {} alquileres penalizados.", expiredRentals.size());
        return expiredRentals.size();
    }

    /**
     * Validates that the user ID is non-blank and represents a valid UUID.
     *
     * @param userId the user ID string to validate
     * @throws IllegalArgumentException if the ID is null, blank, or not a valid UUID
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("El ID de usuario no puede ser nulo o vacío.");
        }
        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El ID de usuario debe ser un UUID válido.", e);
        }
    }

    /**
     * Validates that the requested duration falls within the configured rental bounds.
     *
     * @param durationMinutes the requested duration in minutes
     * @param config the active business configuration snapshot
     * @throws IllegalArgumentException if the duration is zero, negative, or outside configured bounds
     */
    private void verifyRentalDuration(int durationMinutes, BusinessConfigSnapshot config) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor a 0 minutos.");
        }

        if (durationMinutes < config.minRentalDurationMinutes() ||
                durationMinutes > config.maxRentalDurationMinutes()) {
            throw new IllegalArgumentException(
                    "Duración fuera del rango permitido: [%d, %d] minutos."
                            .formatted(config.minRentalDurationMinutes(), config.maxRentalDurationMinutes()));
        }
    }
}
