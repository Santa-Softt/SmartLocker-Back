package com.smartlockr.shared.utils;

import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;

/**
 * Defines cache name constants used across the application.
 * Centralises cache identifiers to avoid magic strings in annotations and configuration.
 */
public final class CacheNames {

    /**
     * Cache name for the active {@link BusinessConfig}.
     */
    public static final String BUSINESS_CONFIG_CACHE = "businessConfig";

    private CacheNames() {}
}
