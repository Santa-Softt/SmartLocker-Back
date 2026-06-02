package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.LockerSize;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RateInputTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("rate valido - sin violaciones")
    void shouldBeValidForValidInput() {
        RateInput input = new RateInput(LockerSize.M, new BigDecimal("5.00"));

        Set<ConstraintViolation<RateInput>> violations = validator.validate(input);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("size null - viola @NotNull")
    void shouldRejectNullSize() {
        RateInput input = new RateInput(null, new BigDecimal("5.00"));

        Set<ConstraintViolation<RateInput>> violations = validator.validate(input);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("size");
    }

    @Test
    @DisplayName("hourlyRate null - viola @NotNull")
    void shouldRejectNullRate() {
        RateInput input = new RateInput(LockerSize.M, null);

        Set<ConstraintViolation<RateInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("hourlyRate");
    }

    @Test
    @DisplayName("hourlyRate negativo - viola @PositiveOrZero")
    void shouldRejectNegativeRate() {
        RateInput input = new RateInput(LockerSize.M, new BigDecimal("-1.00"));

        Set<ConstraintViolation<RateInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("hourlyRate");
    }

    @Test
    @DisplayName("hourlyRate cero - es valido (PositiveOrZero)")
    void shouldAcceptZeroRate() {
        RateInput input = new RateInput(LockerSize.M, BigDecimal.ZERO);

        Set<ConstraintViolation<RateInput>> violations = validator.validate(input);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("toRate - convierte al record Rate")
    void shouldConvertToRate() {
        RateInput input = new RateInput(LockerSize.L, new BigDecimal("15.50"));

        Rate rate = input.toRate();

        assertThat(rate.getSize()).isEqualTo(LockerSize.L);
        assertThat(rate.getHourlyRate()).isEqualByComparingTo(new BigDecimal("15.50"));
    }
}
