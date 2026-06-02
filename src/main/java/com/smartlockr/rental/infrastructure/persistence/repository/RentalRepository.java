package com.smartlockr.rental.infrastructure.persistence.repository;

import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RentalRepository extends JpaRepository<Rental, UUID> {

    /**
     * Finds an active rental (ACTIVE or HOLD) for a specific user.
     *
     * @param userId the user UUID
     * @param states the list of states to search for
     * @return optional containing the rental if found
     */
    Optional<Rental> findByUserIdAndStateIn(UUID userId, List<RentalState> states);

    boolean existsByUserIdAndState(UUID userId, RentalState state);

    boolean existsByUserIdAndStateAndIdNot(UUID userId, RentalState state, UUID rentalId);

    /**
     * Finds all rentals in HOLD state that were created before a specific timestamp.
     *
     * @param state the rental state (should be HOLD)
     * @param expirationTime the threshold timestamp
     * @return list of expired hold rentals
     */
    List<Rental> findAllByStateAndStartTimeBefore(RentalState state, Instant expirationTime);

    /**
     * Finds all rentals in ACTIVE state whose estimated end time has passed.
     *
     * @param state the rental state (should be ACTIVE)
     * @param currentTime the current timestamp
     * @return list of expired active rentals
     */
    List<Rental> findAllByStateAndEstimatedEndTimeBefore(RentalState state, Instant currentTime);

    /**
     * Optimized query to find all expired rentals (both HOLD and ACTIVE) in a single call.
     * Used for high-frequency reconciliation when Redis is unavailable.
     *
     * @param holdState the HOLD state constant
     * @param activeState the ACTIVE state constant
     * @param holdThreshold the threshold for HOLD expiration (startTime before this)
     * @param activeThreshold the threshold for ACTIVE expiration (estimatedEndTime before this)
     * @return list of all expired rentals requiring processing
     */
    @Query("SELECT r FROM Rental r WHERE " +
           "(r.state = :holdState AND r.startTime < :holdThreshold) OR " +
           "(r.state = :activeState AND r.estimatedEndTime < :activeThreshold)")
    List<Rental> findAllExpiredRentals(
            @Param("holdState") RentalState holdState,
            @Param("activeState") RentalState activeState,
            @Param("holdThreshold") Instant holdThreshold,
            @Param("activeThreshold") Instant activeThreshold);
}
