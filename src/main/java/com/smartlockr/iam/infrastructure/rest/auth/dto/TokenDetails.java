package com.smartlockr.iam.infrastructure.rest.auth.dto;

import java.time.Instant;

public record TokenDetails(
        Instant expiresAt
) {
}
