package com.smartlockr.iam.infrastructure.rest.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartlockr.iam.domain.enums.Role;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        Role role,
        boolean hasSeenWelcome,
        boolean suspended,
        Instant suspensionTime,
        boolean receiveReceipts,
        boolean receivesPromotions
) {
}
