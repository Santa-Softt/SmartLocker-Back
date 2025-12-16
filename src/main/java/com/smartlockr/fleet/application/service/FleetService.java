package com.smartlockr.fleet.application.service;

import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {
    private final LockerRepository lockerRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final LockerMapper lockerMapper;

    /**
     * Busca lockers disponibles de un tamaño específico.
     * La lógica de negocio aquí es asegurar que solo se devuelvan lockers 'AVAILABLE'.
     */
    @Transactional(readOnly = true)
    public List<LockerResponse> findAvailableLockersBySize(LockerSize size) {
        if (size == null) {
            throw new IllegalArgumentException("Locker size cannot be null.");
        }
        List<Locker> lockersFound = lockerRepository.findBySizeAndStateOrderByLabelAsc(size, LockerState.AVAILABLE);

        return lockerMapper.toResponseList(lockersFound);
    }

    @Transactional
    public Locker reserveLockerForHold(UUID lockerId) {
        if (lockerId == null) {
            throw new IllegalArgumentException("Locker ID cannot be null.");
        }

        Locker locker = lockerRepository.findByIdWithPessimisticLock(lockerId)
                .orElseThrow(() -> new EntityNotFoundException("Locker not found with ID: " + lockerId));

        if (locker.getState() != LockerState.AVAILABLE) {
            throw new UnavailableLockerException("Locker " + locker.getLabel() + " is not available for reservation.");
        }

        locker.setState(LockerState.HOLD);

        return locker;
    }

    @Transactional
    public void releaseLockerFromHold(UUID lockerId) {
        if (lockerId == null) {
            log.warn("Attempted to release a locker with a null ID.");
            return;
        }

        lockerRepository.findById(lockerId).ifPresent(locker -> {
            if (locker.getState() == LockerState.HOLD) {
                locker.setState(LockerState.AVAILABLE);
            }
        });
    }

    @Transactional(readOnly = true)
    public BusinessConfig getActiveBusinessConfig() {
        return businessConfigRepository.findTheOne()
                .orElseThrow(() -> new IllegalStateException("Critical: Business configuration not found in the database."));
    }
}
