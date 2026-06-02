package com.smartlockr.rental.application.mapper;

import com.smartlockr.fleet.application.mapper.LockerMapper;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.rental.infrastructure.graphql.dto.RentalResponse;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;

@Mapper(componentModel = "spring", uses = LockerMapper.class)
public interface RentalMapper {

    @Mapping(target = "holdExpiresAt", source = "holdExpiration")
    @Mapping(target = "locker", source = "rental.locker", qualifiedByName = "toLockerResponse")
    @Mapping(target = "finalPrice", source = "rental.finalCost")
    @Mapping(target = "rentalId", source = "rental.id")
    @Mapping(target = "isPenalized", ignore = true)
    RentalResponse toActiveRentalResponse(Rental rental, Instant holdExpiration);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", constant = "HOLD")
    @Mapping(target = "isPenalized", constant = "false")
    @Mapping(target = "finalCost", source = "currentPrice")
    Rental toNewHoldRental(User user, Locker locker, Instant startTime, Instant estimatedEndTime, BigDecimal currentPrice);
}
