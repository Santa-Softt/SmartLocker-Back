package com.smartlockr.shared.config;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.properties.BusinessProperties;
import com.smartlockr.shared.utils.LockerLabelUtil;
import com.smartlockr.shared.properties.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessDataLoader implements ApplicationRunner {

    private final LockerRepository lockerRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final BusinessProperties businessProperties;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        if (lockerRepository.count() > 0) {
            log.info("La base de datos ya está inicializada. Omitiendo el seeding de datos.");
            return;
        }
        log.info("Base de datos vacía. Iniciando el seeding de datos del sistema SmartLockr...");
        try {
            createAdminUser();
            createBusinessConfig();
            createInitialLockers();
            log.info("Seeding de datos completado exitosamente.");
        } catch (Exception e) {
            log.error("Error crítico durante el seeding de datos. La aplicación puede no funcionar correctamente.", e);
        }
    }

    private void createAdminUser() {
        String adminEmail = securityProperties.adminEmail();

        User admin = User.builder()
                .email(adminEmail)
                .fullName("SysAdmin")
                .role(Role.ADMIN)
                .hasSeenWelcome(true)
                .avatarUrl("https://lh3.googleusercontent.com/a/ACg8ocKUBzUlUjQWJRQ7H5ipskQNIavhvFbUEHLT4fUIS7fuL5N9hzM=s288-c-no")
                .build();
        userRepository.save(admin);
        log.info("Usuario administrador creado");
    }

    private void createBusinessConfig() {
        List<Rate> defaultRates = businessProperties.rates().entrySet().stream()
                .map(entry -> new Rate(entry.getKey(), entry.getValue()))
                .toList();


        BusinessConfig config = BusinessConfig.builder()
                .holdDurationSeconds(businessProperties.maxHoldDurationSeconds())
                .minRentalDurationMinutes(businessProperties.minRentalDuration())
                .maxRentalDurationMinutes(businessProperties.maxRentalDuration())
                .penaltyPercentage(businessProperties.penaltyPercentage())
                .streakDiscountPercentage(businessProperties.streakDiscountPercentage())
                .streakThreshold(businessProperties.streakThreshold())
                .serviceStatus(ServiceStatus.OPERATIONAL)
                .build();

        config.setRates(defaultRates);

        businessConfigRepository.save(config);
        log.info("Configuración de negocio inicial creada.");
    }

    private void createInitialLockers() {
        var inventory = businessProperties.quantities();

        List<Locker> lockersToCreate = new ArrayList<>();
        inventory.forEach((size, count) -> {
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
