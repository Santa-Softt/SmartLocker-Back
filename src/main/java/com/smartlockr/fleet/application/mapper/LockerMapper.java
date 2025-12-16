package com.smartlockr.fleet.application.mapper;

import com.smartlockr.fleet.infrastructure.graphql.dto.LockerResponse;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LockerMapper {
    LockerResponse toResponse(Locker locker);
    List<LockerResponse> toResponseList(List<Locker> lockers);
}
