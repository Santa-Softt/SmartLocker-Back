package com.smartlockr.fleet.infrastructure.persistence.repository;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LockerRepository extends JpaRepository<Locker, UUID> {
    List<Locker> findAllBySize(LockerSize size);
}
