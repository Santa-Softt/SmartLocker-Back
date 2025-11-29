package com.smartlockr.iam.application.auth.port;

import com.smartlockr.iam.infrastructure.persistence.model.User;

public interface JwtProvider {
    String generateAccessToken(User user);
}
