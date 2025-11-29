package com.smartlockr.iam.application.auth.dto;

public record AuthResponse(String accessToken,
                           String refreshToken) {
}
