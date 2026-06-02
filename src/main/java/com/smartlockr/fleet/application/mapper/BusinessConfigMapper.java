package com.smartlockr.fleet.application.mapper;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.shared.properties.BusinessProperties;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for converting {@link BusinessConfig} to its cache-safe snapshot representation.
 */
@Mapper(componentModel = "spring")
public interface BusinessConfigMapper {

    /**
     * Converts a {@link BusinessConfig} entity to a {@link BusinessConfigSnapshot} for Redis caching.
     *
     * @param config the source business configuration entity
     * @return a serialization-safe snapshot of the configuration
     */
    BusinessConfigSnapshot toSnapshot(BusinessConfig config);

    /**
     * Converts a {@link Rate} entity to a {@link RateSnapshot}.
     *
     * @param rate the source rate entity
     * @return a serialization-safe snapshot of the rate
     */
    RateSnapshot toRateSnapshot(Rate rate);


    /**
     * Creates an initial {@link BusinessConfig} entity from application startup properties.
     *
     * @param properties the validated startup configuration properties
     * @return a new unsaved {@link BusinessConfig} entity
     */
    @Mapping(source = "maxHoldDurationSeconds", target = "holdDurationSeconds")
    @Mapping(source = "minRentalDurationMinutes", target = "minRentalDurationMinutes")
    @Mapping(source = "maxRentalDurationMinutes", target = "maxRentalDurationMinutes")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "serviceStatus", constant = "OPERATIONAL")
    @Mapping(target = "rates", expression = "java(toMutableRateList(properties))")
    BusinessConfig fromProperties(BusinessProperties properties);

    /**
     * Converts the rates map from {@link BusinessProperties} into a mutable list of {@link Rate} entities.
     * Mutability is required by Hibernate to manage the {@code @ElementCollection} lifecycle.
     *
     * @param properties the source business properties
     * @return a mutable list of rates
     */
    default List<Rate> toMutableRateList(BusinessProperties properties) {
        return properties.rates().entrySet().stream()
                .map(entry -> new Rate(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
