package com.smartlockr.fleet.application.service;

import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerCountSummary;
import com.smartlockr.shared.utils.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for managing locker fleet operations,
 * including availability queries, hold reservations, and release flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {

    private final LockerRepository lockerRepository;
    private final LockerMapper lockerMapper;
    private final BusinessService businessService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Returns all available lockers matching the given size, ordered by label.
     *
     * @param size the locker size to filter by
     * @return list of {@link LockerResponse} for available lockers of the given size
     * @throws IllegalArgumentException if size is null
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.LOCKER_AVAILABLE_BY_SIZE_CACHE, key = "#size")
    public List<LockerResponse> findAvailableLockersBySize(LockerSize size) {
        if (size == null) {
            throw new IllegalArgumentException("Locker size cannot be null.");
        }
        List<Locker> lockersFound = lockerRepository.findBySizeAndStateOrderByLabelAsc(size, LockerState.AVAILABLE);
        return lockerMapper.toResponseList(lockersFound);
    }

    @Transactional(readOnly = true)
    public List<LockerResponse> findLockersForAdmin(LockerSize size, LockerState state) {
        List<Locker> lockers = switch (filterType(size, state)) {
            case SIZE_AND_STATE -> lockerRepository.findBySizeAndStateOrderByLabelAsc(size, state);
            case SIZE_ONLY -> lockerRepository.findBySizeOrderByLabelAsc(size);
            case STATE_ONLY -> lockerRepository.findByStateOrderByLabelAsc(state);
            case NO_FILTER -> lockerRepository.findAllByOrderByLabelAsc();
        };
        return lockerMapper.toResponseList(lockers);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.LOCKER_SUMMARY_CACHE, allEntries = true),
            @CacheEvict(value = CacheNames.LOCKER_AVAILABLE_BY_SIZE_CACHE, allEntries = true)
    })
    public LockerResponse updateLockerState(UUID lockerId, LockerState state) {
        if (lockerId == null) {
            throw new IllegalArgumentException("Locker ID cannot be null.");
        }
        if (state == null) {
            throw new IllegalArgumentException("Locker state cannot be null.");
        }

        Locker locker = lockerRepository.findById(lockerId)
                .orElseThrow(() -> new com.smartlockr.fleet.application.exception.LockerNotFoundException(
                        "Locker no encontrado: " + lockerId));

        LockerState previousState = locker.getState();
        locker.setState(state);
        Locker savedLocker = lockerRepository.save(locker);

        if (previousState != state) {
            eventPublisher.publishEvent(new LockerStateChangedEvent(savedLocker.getId(), state));
        }

        return lockerMapper.toResponseList(List.of(savedLocker)).getFirst();
    }

    /**
     * Reserves an available locker of the given size for a hold, using a pessimistic lock
     * to prevent concurrent allocation conflicts.
     *
     * @param lockerSize the requested locker size
     * @return the reserved {@link Locker} in HOLD state
     * @throws UnavailableLockerException if no locker of the requested size is available
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.LOCKER_SUMMARY_CACHE, allEntries = true),
            @CacheEvict(value = CacheNames.LOCKER_AVAILABLE_BY_SIZE_CACHE, key = "#lockerSize")
    })
    public Locker reserveLockerForHold(LockerSize lockerSize) {
        Locker locker = lockerRepository.findAndLockOne(lockerSize, LockerState.AVAILABLE, Limit.of(1))
                .orElseThrow(() -> new UnavailableLockerException(
                        "No hay lockers disponibles para el tamaño solicitado."));

        locker.allocate();
        lockerRepository.save(locker);
        eventPublisher.publishEvent(new LockerStateChangedEvent(locker.getId(), LockerState.HOLD));

        return locker;
    }

    /**
     * Releases a locker from HOLD state back to AVAILABLE.
     * Logs a warning if the locker is not in HOLD state or does not exist.
     *
     * @param lockerId the UUID of the locker to release
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.LOCKER_SUMMARY_CACHE, allEntries = true),
            @CacheEvict(value = CacheNames.LOCKER_AVAILABLE_BY_SIZE_CACHE, allEntries = true)
    })
    public void releaseLockerFromHold(UUID lockerId) {
        if (lockerId == null) {
            log.warn("Attempted to release a locker with a null ID.");
            return;
        }

        int updatedRows = lockerRepository.releaseLockerFromHold(
                lockerId,
                LockerState.HOLD,
                LockerState.AVAILABLE
        );

        if (updatedRows > 0) {
            eventPublisher.publishEvent(new LockerStateChangedEvent(lockerId, LockerState.AVAILABLE));
            log.info("Locker {} liberado exitosamente de estado HOLD.", lockerId);
            return;
        }

        log.warn("Intento de liberar locker {} que no está en estado HOLD o no existe.", lockerId);
    }

    /**
     * Returns a summary of locker availability and pricing per size,
     * based on the active business configuration and current locker counts.
     *
     * @return list of {@link LockerSizeSummaryResponse} sorted by size
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.LOCKER_SUMMARY_CACHE, key = "'available-summary'")
    public List<LockerSizeSummaryResponse> getLockerSizeSummaries() {
        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();
        List<RateSnapshot> rates = config.rates();

        List<LockerCountSummary> counts = lockerRepository.countByStateGroupedBySize(LockerState.AVAILABLE);

        Map<LockerSize, Long> availabilityMap = counts.stream()
                .collect(Collectors.toMap(LockerCountSummary::size, LockerCountSummary::count));

        return rates.stream()
                .map(rate -> lockerMapper.toSummaryResponse(
                        rate,
                        availabilityMap.getOrDefault(rate.size(), 0L).intValue())
                )
                .sorted(Comparator.comparing(LockerSizeSummaryResponse::size))
                .toList();
    }

    private AdminLockerFilterType filterType(LockerSize size, LockerState state) {
        if (size != null && state != null) {
            return AdminLockerFilterType.SIZE_AND_STATE;
        }
        if (size != null) {
            return AdminLockerFilterType.SIZE_ONLY;
        }
        if (state != null) {
            return AdminLockerFilterType.STATE_ONLY;
        }
        return AdminLockerFilterType.NO_FILTER;
    }

    private enum AdminLockerFilterType {
        SIZE_AND_STATE,
        SIZE_ONLY,
        STATE_ONLY,
        NO_FILTER
    }
}

