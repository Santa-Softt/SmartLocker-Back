package com.smartlockr.iam.application.dto;

public record UpdateUserSettings(
        String fullName,
        String avatarUrl,
        boolean hasSeenWelcome,
        boolean receiveReceipts,
        boolean receivesPromotions
) {}