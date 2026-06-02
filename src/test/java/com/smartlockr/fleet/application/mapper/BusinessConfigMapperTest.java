package com.smartlockr.fleet.application.mapper;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
import com.smartlockr.shared.properties.BusinessProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessConfigMapperTest {

    private BusinessConfigMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(BusinessConfigMapper.class);
    }

    @Test
    @DisplayName("toRateSnapshot - convierte Rate entity a RateSnapshot")
    void shouldMapRateToSnapshot() {
        Rate rate = new Rate(LockerSize.M, new BigDecimal("7.50"));

        RateSnapshot snapshot = mapper.toRateSnapshot(rate);

        assertThat(snapshot.size()).isEqualTo(LockerSize.M);
        assertThat(snapshot.hourlyRate()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    @DisplayName("fromProperties - crea BusinessConfig desde BusinessProperties con rates mutables")
    void shouldMapFromProperties() {
        Map<LockerSize, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(LockerSize.S, new BigDecimal("3.00"));
        rates.put(LockerSize.M, new BigDecimal("5.00"));
        Map<LockerSize, Integer> quantities = new LinkedHashMap<>();
        quantities.put(LockerSize.S, 10);
        quantities.put(LockerSize.M, 20);

        BusinessProperties props = new BusinessProperties(
                300, 15, 1440, 10, 5, 5, rates, quantities);

        BusinessConfig config = mapper.fromProperties(props);

        assertThat(config.getId()).isNull();
        assertThat(config.getHoldDurationSeconds()).isEqualTo(300);
        assertThat(config.getMinRentalDurationMinutes()).isEqualTo(15);
        assertThat(config.getMaxRentalDurationMinutes()).isEqualTo(1440);
        assertThat(config.getServiceStatus()).isEqualTo(ServiceStatus.OPERATIONAL);
        assertThat(config.getRates()).hasSize(2);
        assertThat(config.getRates()).extracting(Rate::getSize).contains(LockerSize.S, LockerSize.M);
    }

    @Test
    @DisplayName("toMutableRateList - devuelve lista mutable con Rates")
    void shouldReturnMutableRateList() {
        Map<LockerSize, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(LockerSize.L, new BigDecimal("10.00"));
        rates.put(LockerSize.XL, new BigDecimal("15.00"));
        BusinessProperties props = new BusinessProperties(
                300, 15, 1440, 10, 5, 5, rates, Map.of());

        BusinessConfig config = mapper.fromProperties(props);

        assertThat(config.getRates()).isInstanceOf(java.util.ArrayList.class);
        assertThat(config.getRates()).hasSize(2);
    }

    @Test
    @DisplayName("toSnapshot - mapea BusinessConfig a BusinessConfigSnapshot")
    void shouldMapToSnapshot() {
        BusinessConfig config = BusinessConfig.builder()
                .id(UUID.randomUUID())
                .holdDurationSeconds(300)
                .minRentalDurationMinutes(15)
                .maxRentalDurationMinutes(1440)
                .penaltyPercentage(10)
                .streakThreshold(5)
                .streakDiscountPercentage(5)
                .serviceStatus(ServiceStatus.OPERATIONAL)
                .rates(List.of(new Rate(LockerSize.M, new BigDecimal("5.00"))))
                .build();

        var snapshot = mapper.toSnapshot(config);

        assertThat(snapshot.holdDurationSeconds()).isEqualTo(300);
        assertThat(snapshot.minRentalDurationMinutes()).isEqualTo(15);
        assertThat(snapshot.maxRentalDurationMinutes()).isEqualTo(1440);
        assertThat(snapshot.penaltyPercentage()).isEqualTo(10);
        assertThat(snapshot.rates()).hasSize(1);
        assertThat(snapshot.rates().get(0).size()).isEqualTo(LockerSize.M);
    }
}
