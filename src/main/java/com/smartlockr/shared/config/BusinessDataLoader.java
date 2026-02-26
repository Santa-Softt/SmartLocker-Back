package com.smartlockr.shared.config;

import com.smartlockr.fleet.application.mapper.BusinessConfigMapper;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.dto.UpdateBusinessConfigCommand;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.properties.BusinessProperties;
import com.smartlockr.shared.properties.SecurityProperties;
import com.smartlockr.shared.utils.LockerLabelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the database with initial system data on application startup.
 * Each seeding step is guarded independently to allow safe re-runs
 * and to recover from partial initialisation failures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessDataLoader implements ApplicationRunner {

    private final LockerRepository lockerRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final BusinessProperties businessProperties;
    private final BusinessConfigMapper businessConfigMapper;
    private final BusinessService businessService;

    /**
     * Entry point invoked by Spring on application startup.
     * Runs all seeding steps sequentially, each guarded by its own existence check.
     *
     * @param args application startup arguments
     */
    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        log.info("Verificando estado de inicialización del sistema SmartLockr...");
        try {
            createAdminUserIfAbsent();
            createBusinessConfigIfAbsent();
            syncBusinessConfigFromProperties();
            createInitialLockersIfAbsent();
            log.info("Verificación de datos completada exitosamente.");
        } catch (Exception e) {
            log.error("Error crítico durante el seeding de datos. La aplicación puede no funcionar correctamente.", e);
        }
    }

    /**
     * Synchronises the persisted business configuration with the current application properties.
     * Ensures that changes made to application.yml are reflected in the database on every startup
     * without requiring manual database intervention.
     */
    private void syncBusinessConfigFromProperties() {
        log.info("Sincronizando BusinessConfig...");
        businessConfigRepository.findTheOne().ifPresent(existingConfig -> {
            BusinessConfig updatedValues = businessConfigMapper.fromProperties(businessProperties);
            existingConfig.applyUpdate(new UpdateBusinessConfigCommand(
                    updatedValues.getHoldDurationSeconds(),
                    updatedValues.getMinRentalDurationMinutes(),
                    updatedValues.getMaxRentalDurationMinutes(),
                    updatedValues.getPenaltyPercentage(),
                    updatedValues.getStreakDiscountPercentage(),
                    updatedValues.getStreakThreshold(),
                    updatedValues.getServiceStatus(),
                    updatedValues.getRates()
            ));
            businessService.saveConfig(existingConfig);
            log.info("BusinessConfig sincronizada desde propiedades de configuración.");
        });
    }

    /**
     * Creates the system administrator user if no admin account exists.
     */
    private void createAdminUserIfAbsent() {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Usuario administrador ya existe. Omitiendo.");
            return;
        }

        User admin = User.builder()
                .email(securityProperties.adminEmail())
                .fullName("SysAdmin")
                .role(Role.ADMIN)
                .hasSeenWelcome(true)
                .avatarUrl("https://lh3.googleusercontent.com/a/ACg8ocKUBzUlUjQWJRQ7H5ipskQNIavhvFbUEHLT4fUIS7fuL5N9hzM=s288-c-no")
                .userPreferences(UserPreferences.builder()
                        .receiveReceipts(true)
                        .receivesPromotions(false)
                        .build())
                .build();

        userRepository.save(admin);
        log.info("Usuario administrador creado.");
    }

    /**
     * Seeds the initial business configuration from application properties
     * if no configuration exists in the database.
     */
    private void createBusinessConfigIfAbsent() {
        if (businessConfigRepository.findTheOne().isPresent()) {
            log.info("BusinessConfig ya existe. Omitiendo.");
            return;
        }
        BusinessConfig config = businessConfigMapper.fromProperties(businessProperties);
        businessService.saveConfig(config);
        log.info("Configuración de negocio inicial creada desde propiedades.");
    }

    /**
     * Creates the initial locker inventory from the configured quantities per size
     * if no lockers exist in the database.
     */
    private void createInitialLockersIfAbsent() {
        if (lockerRepository.count() > 0) {
            log.info("Lockers ya existen. Omitiendo.");
            return;
        }

        List<Locker> lockersToCreate = new ArrayList<>();
        businessProperties.quantities().forEach((size, count) -> {
            for (int i = 1; i <= count; i++) {
                Locker locker = Locker.builder()
                        .size(size)
                        .state(LockerState.AVAILABLE)
                        .label(LockerLabelUtil.generate(size, i))
                        .build();
                lockersToCreate.add(locker);
            }
        });

        lockerRepository.saveAll(lockersToCreate);
        log.info("{} lockers creados y disponibles en el sistema.", lockersToCreate.size());
    }
}
