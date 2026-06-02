package com.smartlockr.shared.utils;

import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.iam.infrastructure.persistence.model.User;

/**
 * Defines cache name constants used across the application.
 * Centralises cache identifiers to avoid magic strings in annotations and configuration.
 */
public final class CacheNames {

    /**
     * Cache name for the active {@link BusinessConfig}.
     */
    public static final String BUSINESS_CONFIG_CACHE = "businessConfig";

    /**
     * Cache name for the active {@link User}.
     */
    public static final String USER_CACHE = "user";

    /**
     * Cache name for locker availability summary by size.
     */
    public static final String LOCKER_SUMMARY_CACHE = "lockerSummary";

    /**
     * Cache name for available lockers filtered by size.
     */
    public static final String LOCKER_AVAILABLE_BY_SIZE_CACHE = "lockerAvailableBySize";

    private CacheNames() {}
}
