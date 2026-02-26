package com.smartlockr.fleet.application.service;

import com.smartlockr.fleet.application.exception.MissingBusinessConfigException;
import com.smartlockr.fleet.application.mapper.BusinessConfigMapper;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.dto.UpdateBusinessConfigCommand;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.shared.utils.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for managing the business configuration lifecycle.
 * Provides read access to the active configuration with Redis-backed caching,
 * and ensures cache coherence on updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessConfigRepository businessConfigRepository;
    private final BusinessConfigMapper businessConfigMapper;

    /**
     * Returns a snapshot of the active business configuration.
     * Results are cached in Redis under {@link CacheNames#BUSINESS_CONFIG_CACHE}.
     * The database is only queried on a cache miss.
     *
     * @return a {@link BusinessConfigSnapshot} representing the active configuration
     * @throws MissingBusinessConfigException if no configuration is found in the database
     */
    @Cacheable(value = CacheNames.BUSINESS_CONFIG_CACHE, key = "'active'")
    @Transactional(readOnly = true)
    public BusinessConfigSnapshot getActiveBusinessConfig() {
        log.debug("Cache miss — loading BusinessConfig from database.");
        BusinessConfig config = businessConfigRepository.findTheOne()
                .orElseThrow(() -> {
                    log.error("CRITICAL: No active BusinessConfig found. System cannot operate.");
                    return new MissingBusinessConfigException(
                            "Configuración de negocio no encontrada en base de datos.");
                });
        return businessConfigMapper.toSnapshot(config);
    }

    /**
     * Updates the active business configuration and evicts the cache to ensure coherence.
     *
     * @param command the update command carrying the new configuration values
     * @return the updated and persisted {@link BusinessConfig}
     * @throws MissingBusinessConfigException if no active configuration exists to update
     */
    @CacheEvict(value = CacheNames.BUSINESS_CONFIG_CACHE, allEntries = true)
    @Transactional
    public BusinessConfig updateBusinessConfig(UpdateBusinessConfigCommand command) {
        BusinessConfig config = businessConfigRepository.findTheOne()
                .orElseThrow(() -> new MissingBusinessConfigException(
                        "No existe configuración activa para actualizar."));

        config.applyUpdate(command);

        BusinessConfig saved = businessConfigRepository.save(config);
        log.info("BusinessConfig actualizada. Cache invalidada.");
        return saved;
    }

    /**
     * Persists a new business configuration and evicts the cache to ensure coherence.
     * Intended for initial seeding or programmatic configuration creation.
     *
     * @param config the fully constructed {@link BusinessConfig} entity to persist
     */
    @CacheEvict(value = CacheNames.BUSINESS_CONFIG_CACHE, allEntries = true)
    @Transactional
    public void saveConfig(BusinessConfig config) {
        businessConfigRepository.save(config);
        log.info("BusinessConfig guardada. Cache invalidada.");
    }
}
