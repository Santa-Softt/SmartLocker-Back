package com.smartlockr.fleet.infrastructure.persistence.repository;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerCountSummary;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LockerRepository extends JpaRepository<Locker, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Locker l WHERE l.size = :size AND l.state = :state")
    Optional<Locker> findAndLockOne(
            @Param("size") LockerSize size,
            @Param("state") LockerState state,
            Limit limit
    );

    List<Locker> findBySizeAndStateOrderByLabelAsc(LockerSize size, LockerState lockerState);

    @Query("""
        SELECT new com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerCountSummary(
            l.size, COUNT(l)
        )
        FROM Locker l
        WHERE l.state = :state
        GROUP BY l.size
    """)
    List<LockerCountSummary> countByStateGroupedBySize(@Param("state") LockerState state);
}
