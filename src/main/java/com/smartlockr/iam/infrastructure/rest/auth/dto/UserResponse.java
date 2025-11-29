package com.smartlockr.iam.infrastructure.rest.auth.dto;

import com.smartlockr.iam.domain.enums.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        Role role
) {
}
