package com.smartlockr.fleet.infrastructure.graphql.dto;

import com.smartlockr.fleet.domain.enums.LockerState;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateLockerStateInputTest {

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
    @DisplayName("input valido - sin violaciones")
    void shouldBeValidForValidInput() {
        UpdateLockerStateInput input = new UpdateLockerStateInput(UUID.randomUUID(), LockerState.MAINTENANCE);

        Set<ConstraintViolation<UpdateLockerStateInput>> violations = validator.validate(input);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("lockerId null - viola @NotNull")
    void shouldRejectNullLockerId() {
        UpdateLockerStateInput input = new UpdateLockerStateInput(null, LockerState.AVAILABLE);

        Set<ConstraintViolation<UpdateLockerStateInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("lockerId");
    }

    @Test
    @DisplayName("state null - viola @NotNull")
    void shouldRejectNullState() {
        UpdateLockerStateInput input = new UpdateLockerStateInput(UUID.randomUUID(), null);

        Set<ConstraintViolation<UpdateLockerStateInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("state");
    }
}
