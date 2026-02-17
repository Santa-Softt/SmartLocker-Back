package com.smartlockr.rental.application.service;

import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.application.service.FleetService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.rental.application.mapper.RentalMapper;
import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.rental.infrastructure.persistence.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalService {
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final FleetService fleetService;
    private final BusinessService businessService;
    private final RentalMapper rentalMapper;

    @Transactional
    public RentalResponse initiateHold(LockerSize size, String userId, Integer durationMinutes) {

        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor a 0 minutos.");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        BusinessConfig config = businessService.getActiveBusinessConfig();

        if (durationMinutes < config.getMinRentalDurationMinutes() ||
                durationMinutes > config.getMaxRentalDurationMinutes()) {
            throw new IllegalArgumentException("Duración no permitida por configuración.");
        }

        Locker lockedLocker = fleetService.reserveLockerForHold(size);

        Instant now = Instant.now();

        Instant serviceExpiration = now.plus(durationMinutes, ChronoUnit.MINUTES);

        Instant holdExpiration = now.plusSeconds(config.getHoldDurationSeconds());

        Rental rental = rentalMapper.toNewHoldRental(
                user,
                lockedLocker,
                now,
                serviceExpiration
        );

        Rental savedRental = rentalRepository.save(rental);

        return rentalMapper.toActiveRentalResponse(savedRental, holdExpiration);
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
}
