package com.smartlockr.fleet.infrastructure.persistence.repository;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LockerRepository extends JpaRepository<Locker, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Locker l WHERE l.id = :id")
    Optional<Locker> findByIdWithPessimisticLock(UUID id);

    List<Locker> findBySizeAndStateOrderByLabelAsc(LockerSize size, LockerState lockerState);
}
