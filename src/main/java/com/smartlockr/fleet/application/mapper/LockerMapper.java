package com.smartlockr.fleet.application.mapper;

import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.application.event.LockerStateChangedEvent;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.graphql.dto.LockerSizeSummaryResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.dto.LockerUpdateResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LockerMapper {

    /**
     * Mapea una lista de Lockers.
     */
    List<LockerResponse> toResponseList(List<Locker> lockers);

    /**
     * Builds a locker size summary response from a rate snapshot and current availability count.
     *
     * @param rate the rate snapshot containing size and hourly rate
     * @param availableCount the number of currently available lockers of that size
     * @return a {@link LockerSizeSummaryResponse}
     */
    @Mapping(source = "rate.size", target = "size")
    @Mapping(source = "rate.hourlyRate", target = "hourlyRate")
    @Mapping(source = "availableCount", target = "availableCount")
    LockerSizeSummaryResponse toSummaryResponse(RateSnapshot rate, int availableCount);

    /**
     * Mapea el evento de dominio a un DTO ligero para la suscripción de GraphQL.
     */
    @Mapping(target = "id", source = "event.lockerId")
    @Mapping(target = "state", source = "event.newState")
    LockerUpdateResponse toUpdateResponse(LockerStateChangedEvent event);
}
