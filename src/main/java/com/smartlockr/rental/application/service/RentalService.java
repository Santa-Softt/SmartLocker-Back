package com.smartlockr.rental.application.service;

import com.smartlockr.fleet.application.service.FleetService;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalService {
    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final FleetService fleetService;
    private final RentalMapper rentalMapper;

    @Transactional
    public RentalResponse initiateHold(UUID lockerId, String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        BusinessConfig config = fleetService.getActiveBusinessConfig();
        Locker lockedLocker = fleetService.reserveLockerForHold(lockerId);

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(config.getHoldDurationSeconds());

        Rental rental = rentalMapper.toNewHoldRental(
                user,
                lockedLocker,
                now,
                expiration);

        return rentalMapper.toActiveRentalResponse(rentalRepository.save(rental));
    }

    @Transactional
    public int processExpiredHolds() {
        BusinessConfig config = fleetService.getActiveBusinessConfig();
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
