package com.smartlockr.support.infrastructure.persistence.repository;

import com.smartlockr.support.infrastructure.persistence.model.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, UUID> {
}
