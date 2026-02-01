package com.smartlockr.fleet.application.mapper;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
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
     * Mapea un Rate y un conteo externo a un DTO de resumen.
     */
    @Mapping(target = "availableCount", source = "count")
    LockerSizeSummaryResponse toSummaryResponse(Rate rate, int count);

    /**
     * Mapea el evento de dominio a un DTO ligero para la suscripción de GraphQL.
     */
    @Mapping(target = "id", source = "event.lockerId")
    @Mapping(target = "state", source = "event.newState")
    LockerUpdateResponse toUpdateResponse(LockerStateChangedEvent event);
}
