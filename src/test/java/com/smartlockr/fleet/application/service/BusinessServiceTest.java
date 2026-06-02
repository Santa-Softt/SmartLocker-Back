package com.smartlockr.fleet.application.service;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.application.exception.MissingBusinessConfigException;
import com.smartlockr.fleet.application.mapper.BusinessConfigMapper;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.infrastructure.dto.UpdateBusinessConfigCommand;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessConfigRepository repository;
    @Mock
    private BusinessConfigMapper mapper;

    private BusinessService service;

    @BeforeEach
    void setUp() {
        service = new BusinessService(repository, mapper);
    }

    @Test
    @DisplayName("getActiveBusinessConfig - returns mapped snapshot")
    void shouldReturnActiveBusinessConfigSnapshot() {
        BusinessConfig config = config();
        BusinessConfigSnapshot snapshot = snapshot(config.getId());
        given(repository.findTheOne()).willReturn(Optional.of(config));
        given(mapper.toSnapshot(config)).willReturn(snapshot);

        assertThat(service.getActiveBusinessConfig()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("getActiveBusinessConfig - fails when config is missing")
    void shouldFailWhenBusinessConfigIsMissing() {
        given(repository.findTheOne()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveBusinessConfig())
                .isInstanceOf(MissingBusinessConfigException.class)
                .hasMessageContaining("Configuración");
    }

    @Test
    @DisplayName("updateActiveBusinessConfig - applies command, saves and maps snapshot")
    void shouldUpdateActiveBusinessConfig() {
        BusinessConfig config = config();
        UpdateBusinessConfigCommand command = new UpdateBusinessConfigCommand(
                600,
                30,
                2880,
                20,
                10,
                3,
                ServiceStatus.PREVENTIVE_MAINTENANCE,
                List.of(new Rate(LockerSize.L, BigDecimal.valueOf(200)))
        );
        BusinessConfigSnapshot snapshot = snapshot(config.getId());
        given(repository.findTheOne()).willReturn(Optional.of(config));
        given(repository.save(config)).willReturn(config);
        given(mapper.toSnapshot(config)).willReturn(snapshot);

        assertThat(service.updateActiveBusinessConfig(command)).isEqualTo(snapshot);
        assertThat(config.getHoldDurationSeconds()).isEqualTo(600);
        assertThat(config.getServiceStatus()).isEqualTo(ServiceStatus.PREVENTIVE_MAINTENANCE);
        assertThat(config.getRates()).hasSize(1);
        then(repository).should().save(config);
    }

    @Test
    @DisplayName("saveConfig - delegates persistence")
    void shouldSaveConfig() {
        BusinessConfig config = config();

        service.saveConfig(config);

        then(repository).should().save(config);
    }

    private BusinessConfig config() {
        return BusinessConfig.builder()
                .id(UUID.randomUUID())
                .holdDurationSeconds(300)
                .minRentalDurationMinutes(15)
                .maxRentalDurationMinutes(1440)
                .penaltyPercentage(10)
                .streakThreshold(5)
                .streakDiscountPercentage(5)
                .serviceStatus(ServiceStatus.OPERATIONAL)
                .rates(List.of(new Rate(LockerSize.M, BigDecimal.valueOf(100))))
                .build();
    }

    private BusinessConfigSnapshot snapshot(UUID id) {
        return new BusinessConfigSnapshot(
                id,
                300,
                15,
                1440,
                10,
                5,
                5,
                ServiceStatus.OPERATIONAL,
                List.of(new RateSnapshot(LockerSize.M, BigDecimal.valueOf(100)))
        );
    }
}
