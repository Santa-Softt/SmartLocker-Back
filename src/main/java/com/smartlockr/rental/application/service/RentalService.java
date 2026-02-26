package com.smartlockr.rental.application.service;

import com.smartlockr.billing.application.service.PricingService;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
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

    @Transactional
    public RentalResponse initiateHold(LockerSize size, String userId, Integer durationMinutes) {
        validateUserId(userId);

        var config = businessService.getActiveBusinessConfig();

        verifyRentalDuration(durationMinutes, config);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        Locker lockedLocker = fleetService.reserveLockerForHold(size);

        Instant now = Instant.now();
        Instant holdExpiration = now.plusSeconds(config.getHoldDurationSeconds());
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
        redisTemplate.opsForValue().set(redisKey, "PENDING", config.getHoldDurationSeconds(), TimeUnit.SECONDS);
        log.info("Clave transaccional generada en Redis: {} con TTL de {} segundos", redisKey, config.getHoldDurationSeconds());

        return rentalMapper.toActiveRentalResponse(savedRental, holdExpiration);
    }

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

    private void verifyRentalDuration(Integer durationMinutes, BusinessConfig config ){
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor a 0 minutos.");
        }

        if (durationMinutes < config.getMinRentalDurationMinutes() ||
                durationMinutes > config.getMaxRentalDurationMinutes()) {
            throw new IllegalArgumentException("Duración no permitida por configuración.");
        }
    }

    @Transactional
    public RentalHoldResponse cancelUserHold(UUID rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Alquiler no encontrado para ID: " + rentalId));

        if (rental.getState() != RentalState.HOLD) {
            throw new IllegalLockerChangeStateException("Solo se pueden cancelar operaciones en estado de retención (HOLD).");
        }

        rental.setState(RentalState.CANCELLED);
        fleetService.releaseLockerFromHold(rental.getLocker().getId());
        rentalRepository.save(rental);

        redisTemplate.delete(HOLD_KEY_PREFIX + rentalId);
        log.info("Hold cancelado por el usuario. Clave de Redis eliminada preventivamente para: {}", rentalId);

        return new RentalHoldResponse("Locker liberado exitosamente");
    }

    /**
     * Se ejecuta cuando Redis notifica que el tiempo de espera (HOLD) ha expirado.
     * Cancela la reserva y libera el locker para que otros puedan usarlo.
     *
     * @param rentalId El UUID del alquiler en estado HOLD.
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

    @Transactional
    public int processExpiredHolds() {
        BusinessConfig config = businessService.getActiveBusinessConfig();
        Instant expirationThreshold = Instant.now().minusSeconds(config.getHoldDurationSeconds());

        List<Rental> expiredRentals = rentalRepository.findAllByStateAndStartTimeBefore(RentalState.HOLD, expirationThreshold);

        if (expiredRentals.isEmpty()) {
            return 0;
        }

        for (Rental rental : expiredRentals) {
            rental.setState(RentalState.CANCELLED);
            fleetService.releaseLockerFromHold(rental.getLocker().getId());
        }

        return expiredRentals.size();
    }

    /**
     * Se ejecuta cuando Redis notifica que el tiempo de uso (ACTIVE) ha finalizado.
     * Cambia el estado a PENALIZED para bloquear acciones y marcar deuda.
     *
     * @param rentalId El UUID del alquiler activo.
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

    @Transactional
    public int processExpiredRentalsToPenalty() {
        List<Rental> expiredRentals = rentalRepository.findAllByStateAndEstimatedEndTimeBefore(
                RentalState.ACTIVE, Instant.now()
        );

        if (expiredRentals.isEmpty()) {
            return 0;
        }

        for (Rental rental : expiredRentals) {
            rental.setState(RentalState.PENALIZED);
            rental.setPenalized(true);
            log.info("Alquiler finalizado por job de reconciliación, aplicando PENALIDAD: {}", rental.getId());
        }

        return expiredRentals.size();
    }
}
