package com.smartlockr.rental.application.mapper;

import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring", uses = LockerMapper.class)
public interface RentalMapper {

    @Mapping(target = "holdExpiresAt", source = "holdExpiration")
    @Mapping(target = "locker", source = "rental.locker")
    RentalResponse toActiveRentalResponse(Rental rental, Instant holdExpiration);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "locker", source = "locker")
    @Mapping(target = "startTime", source = "startTime")
    @Mapping(target = "estimatedEndTime", source = "estimatedEndTime")
    @Mapping(target = "state", constant = "HOLD")
    @Mapping(target = "isPenalized", constant = "false")
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "finalCost", ignore = true)
    Rental toNewHoldRental(User user, Locker locker, Instant startTime, Instant estimatedEndTime);
}
