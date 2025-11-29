package com.smartlockr.iam.application.auth.dto;

import com.smartlockr.iam.infrastructure.persistence.model.User;

public record RotationResult(String newRawRefreshToken,
                             User user) {
}
