package com.smartlockr.iam.application.auth.dto;

import com.smartlockr.iam.infrastructure.persistence.model.RefreshToken;

public record CreatedRefreshToken(RefreshToken entity, String rawToken) {
}
