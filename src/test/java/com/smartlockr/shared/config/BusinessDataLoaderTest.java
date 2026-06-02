package com.smartlockr.shared.config;

import com.smartlockr.fleet.application.mapper.BusinessConfigMapper;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.fleet.infrastructure.persistence.repository.BusinessConfigRepository;
import com.smartlockr.fleet.infrastructure.persistence.repository.LockerRepository;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.repository.UserRepository;
import com.smartlockr.shared.properties.BusinessProperties;
import com.smartlockr.shared.properties.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BusinessDataLoaderTest {

    @Mock
    private LockerRepository lockerRepository;

    @Mock
    private BusinessConfigRepository businessConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessService businessService;

    @Mock
    private ApplicationArguments applicationArguments;

    private BusinessConfigMapper businessConfigMapper;
    private SecurityProperties securityProperties;
    private BusinessProperties businessProperties;
    private BusinessDataLoader dataLoader;

    @BeforeEach
    void setUp() {
        businessConfigMapper = Mappers.getMapper(BusinessConfigMapper.class);
        securityProperties = new SecurityProperties(
                false, "secret", "issuer", "audience",
                Duration.ofMinutes(15), Duration.ofDays(7),
                32, "http://localhost", "admin@smartlockr.com");

        Map<LockerSize, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(LockerSize.S, BigDecimal.valueOf(3));
        rates.put(LockerSize.M, BigDecimal.valueOf(5));
        Map<LockerSize, Integer> quantities = new LinkedHashMap<>();
        quantities.put(LockerSize.S, 2);
        quantities.put(LockerSize.M, 3);
        businessProperties = new BusinessProperties(300, 15, 1440, 10, 5, 5, rates, quantities);

        dataLoader = new BusinessDataLoader(
                lockerRepository, businessConfigRepository, userRepository,
                securityProperties, businessProperties, businessConfigMapper, businessService);
    }

    @Test
    @DisplayName("run - crea admin, business config y lockers si la base esta vacia")
    void shouldSeedEverythingWhenDatabaseIsEmpty() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(false);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.empty());
        given(lockerRepository.count()).willReturn(0L);

        dataLoader.run(applicationArguments);

        then(userRepository).should().save(any());
        then(businessService).should().saveConfig(any(BusinessConfig.class));
        then(lockerRepository).should().saveAll(any());
    }

    @Test
    @DisplayName("run - omite admin si ya existe")
    void shouldSkipAdminWhenExists() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.empty());
        given(lockerRepository.count()).willReturn(0L);

        dataLoader.run(applicationArguments);

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("run - sincroniza business config existente exactamente una vez (sync + skip create)")
    void shouldSyncExistingBusinessConfigOnce() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.of(existingConfig()));
        given(lockerRepository.count()).willReturn(1L);

        dataLoader.run(applicationArguments);

        // syncBusinessConfigFromProperties is always invoked and calls saveConfig exactly once
        then(businessService).should(times(1)).saveConfig(any(BusinessConfig.class));
    }

    @Test
    @DisplayName("run - sincroniza business config existente con las properties actuales")
    void shouldSyncBusinessConfigFromProperties() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.of(existingConfig()));
        given(lockerRepository.count()).willReturn(1L);

        dataLoader.run(applicationArguments);

        then(businessService).should(times(1)).saveConfig(any(BusinessConfig.class));
    }

    @Test
    @DisplayName("run - omite crear lockers si ya existen")
    void shouldSkipLockersWhenAlreadyExist() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.of(existingConfig()));
        given(lockerRepository.count()).willReturn(88L);

        dataLoader.run(applicationArguments);

        then(lockerRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("run - crea la cantidad exacta de lockers segun las quantities")
    void shouldCreateExactNumberOfLockers() {
        given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);
        given(businessConfigRepository.findTheOne()).willReturn(Optional.of(existingConfig()));
        given(lockerRepository.count()).willReturn(0L);

        dataLoader.run(applicationArguments);

        // 2 S + 3 M = 5 lockers
        org.mockito.ArgumentCaptor<List<Locker>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        then(lockerRepository).should().saveAll(captor.capture());
        assertThatSize(captor.getValue(), 5);
    }

    @Test
    @DisplayName("run - captura excepciones y no las propaga")
    void shouldCatchExceptionsGracefully() {
        given(userRepository.existsByRole(Role.ADMIN))
                .willThrow(new RuntimeException("DB connection failed"));

        dataLoader.run(applicationArguments);

        verifyNoInteractions(lockerRepository);
    }

    private BusinessConfig existingConfig() {
        return BusinessConfig.builder()
                .id(UUID.randomUUID())
                .holdDurationSeconds(300)
                .minRentalDurationMinutes(15)
                .maxRentalDurationMinutes(1440)
                .penaltyPercentage(10)
                .streakThreshold(5)
                .streakDiscountPercentage(5)
                .serviceStatus(ServiceStatus.OPERATIONAL)
                .rates(List.of(new com.smartlockr.billing.infrastructure.persistence.model.entity.Rate(LockerSize.M, BigDecimal.TEN)))
                .build();
    }

    private static void assertThatSize(List<?> list, int expected) {
        if (list.size() != expected) {
            throw new AssertionError("Expected size " + expected + " but was " + list.size());
        }
    }
}
