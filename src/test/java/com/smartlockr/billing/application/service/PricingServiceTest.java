package com.smartlockr.billing.application.service;

import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.dto.RateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private BusinessService businessService;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(businessService);
    }

    @Test
    @DisplayName("calculateTotalPrice - calculates proportional price rounded up")
    void shouldCalculateProportionalPriceRoundedUp() {
        given(businessService.getActiveBusinessConfig()).willReturn(configWithRates(
                List.of(new RateSnapshot(LockerSize.M, BigDecimal.valueOf(120)))
        ));

        BigDecimal result = pricingService.calculateTotalPrice(LockerSize.M, 45);

        assertThat(result).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("calculateTotalPrice - rejects non-positive durations")
    void shouldRejectNonPositiveDuration() {
        assertThatThrownBy(() -> pricingService.calculateTotalPrice(LockerSize.M, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
    }

    @Test
    @DisplayName("calculateTotalPrice - rejects missing rates")
    void shouldRejectMissingRates() {
        given(businessService.getActiveBusinessConfig()).willReturn(configWithRates(List.of()));

        assertThatThrownBy(() -> pricingService.calculateTotalPrice(LockerSize.M, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tarifas");
    }

    @Test
    @DisplayName("calculateTotalPrice - rejects missing size rate")
    void shouldRejectMissingRateForSize() {
        given(businessService.getActiveBusinessConfig()).willReturn(configWithRates(
                List.of(new RateSnapshot(LockerSize.S, BigDecimal.TEN))
        ));

        assertThatThrownBy(() -> pricingService.calculateTotalPrice(LockerSize.M, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(LockerSize.M.name());
    }

    private BusinessConfigSnapshot configWithRates(List<RateSnapshot> rates) {
        return new BusinessConfigSnapshot(
                UUID.randomUUID(),
                300,
                15,
                1440,
                10,
                5,
                5,
                ServiceStatus.OPERATIONAL,
                rates
        );
    }
}
