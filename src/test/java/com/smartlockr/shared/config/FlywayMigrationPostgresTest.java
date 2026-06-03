package com.smartlockr.shared.config;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.PostgresContainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationPostgresTest extends PostgresContainerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessConfigRepository businessConfigRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Test
    void migrationsCreateSchemaAndSeedInitialData() {
        assertThat(userRepository.existsByRole(Role.ADMIN)).isTrue();
        var admin = userRepository.findByEmail("admin@smartlockr.test");
        assertThat(admin).isPresent();
        assertThat(admin.orElseThrow().getId().version()).isEqualTo(7);

        var businessConfig = businessConfigRepository.findTheOne();
        assertThat(businessConfig).isPresent();
        var config = businessConfig.orElseThrow();
        assertThat(config.getId().version()).isEqualTo(7);
        assertThat(config.getRates()).hasSize(5);

        assertThat(lockerRepository.count()).isEqualTo(344);
        assertThat(lockerRepository.findBySizeOrderByLabelAsc(LockerSize.XS)).hasSize(128);
        assertThat(lockerRepository.findBySizeOrderByLabelAsc(LockerSize.S)).hasSize(80);
        assertThat(lockerRepository.findBySizeOrderByLabelAsc(LockerSize.M)).hasSize(72);
        assertThat(lockerRepository.findBySizeOrderByLabelAsc(LockerSize.L)).hasSize(64);
        assertThat(lockerRepository.findBySizeOrderByLabelAsc(LockerSize.XL)).isEmpty();
        assertThat(lockerRepository.findAll())
                .allSatisfy(locker -> assertThat(locker.getId().version()).isEqualTo(7));
    }
}
