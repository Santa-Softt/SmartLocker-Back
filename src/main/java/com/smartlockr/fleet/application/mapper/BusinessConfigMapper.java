package com.smartlockr.fleet.application.mapper;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import org.mapstruct.Mapper;

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
}
