package com.smartlockr.shared.config;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.utils.LockerLabelUtil;
import com.smartlockr.shared.properties.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessDataLoader implements ApplicationRunner {

    private final LockerRepository lockerRepository;
    private final BusinessConfigRepository businessConfigRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

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
                .build();
        userRepository.save(admin);
        log.info("Usuario administrador creado");
    }

    private void createBusinessConfig() {
        BusinessConfig config = BusinessConfig.builder()
                .holdDurationSeconds(120)
                .minRentalDurationMinutes(5)
                .maxRentalDurationMinutes(1440)
                .penaltyPercentage(50)
                .streakDiscountPercentage(10)
                .streakThreshold(7)
                .serviceStatus(ServiceStatus.OPERATIONAL)
                .build();

        List<Rate> defaultRates = List.of(
                new Rate(LockerSize.XS, new BigDecimal("1500")),
                new Rate(LockerSize.S,  new BigDecimal("2000")),
                new Rate(LockerSize.M,  new BigDecimal("3000")),
                new Rate(LockerSize.L,  new BigDecimal("4000")),
                new Rate(LockerSize.XL, new BigDecimal("5000"))
        );
        config.setRates(defaultRates);

        businessConfigRepository.save(config);
        log.info("Configuración de negocio inicial creada.");
    }

    private void createInitialLockers() {
        Map<LockerSize, Integer> inventory = Map.of(
                LockerSize.XS, 20,
                LockerSize.S, 24,
                LockerSize.M, 24,
                LockerSize.L, 12,
                LockerSize.XL, 8
        );

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
