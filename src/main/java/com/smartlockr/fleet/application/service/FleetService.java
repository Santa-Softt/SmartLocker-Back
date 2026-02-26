package com.smartlockr.fleet.application.service;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.application.exception.UnavailableLockerException;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerCountSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetService {

        private final LockerRepository lockerRepository;
        private final LockerMapper lockerMapper;
        private final BusinessService businessService;
        private final ApplicationEventPublisher eventPublisher;

        @Transactional(readOnly = true)
        public List<LockerResponse> findAvailableLockersBySize(LockerSize size) {
            if (size == null) {
                throw new IllegalArgumentException("Locker size cannot be null.");
            }
            List<Locker> lockersFound = lockerRepository.findBySizeAndStateOrderByLabelAsc(size, LockerState.AVAILABLE);
            return lockerMapper.toResponseList(lockersFound);
        }

        @Transactional
        public Locker reserveLockerForHold(LockerSize lockerSize) {
            var locker = lockerRepository.findAndLockOne(lockerSize, LockerState.AVAILABLE, Limit.of(1))
                    .orElseThrow(() -> new UnavailableLockerException("Currently we haven't lockers available for this time"));

            locker.allocate();
            lockerRepository.save(locker);
            eventPublisher.publishEvent(new LockerStateChangedEvent(locker.getId(), LockerState.HOLD));

            return locker;
        }

        @Transactional
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
                log.info("Locker {} liberado exitosamente de estado HOLD", lockerId);
                return;
            }
            log.warn("Intento de liberar locker {} que no está en estado HOLD o no existe", lockerId);

        }

        @Transactional(readOnly = true)
        public List<LockerSizeSummaryResponse> getLockerSizeSummaries() {
            BusinessConfig config = businessService.getActiveBusinessConfig();
            List<Rate> rates = config.getRates();

            List<LockerCountSummary> counts = lockerRepository.countByStateGroupedBySize(LockerState.AVAILABLE);

            Map<LockerSize, Long> availabilityMap = counts.stream()
                    .collect(Collectors.toMap(LockerCountSummary::size, LockerCountSummary::count));

            return rates.stream()
                    .map(rate -> lockerMapper.toSummaryResponse(rate,
                            availabilityMap.getOrDefault(rate.getSize(), 0L).intValue())
                    )
                    .sorted(Comparator.comparing(LockerSizeSummaryResponse::size))
                    .toList();
        }
    }

