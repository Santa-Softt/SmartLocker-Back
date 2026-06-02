package com.smartlockr.support.infrastructure.graphql.dto;

import com.smartlockr.support.domain.enums.TicketStatus;
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

class UpdateTicketStatusInputTest {

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
        UpdateTicketStatusInput input = new UpdateTicketStatusInput(UUID.randomUUID(), TicketStatus.CLOSED);

        Set<ConstraintViolation<UpdateTicketStatusInput>> violations = validator.validate(input);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("ticketId null - viola @NotNull")
    void shouldRejectNullTicketId() {
        UpdateTicketStatusInput input = new UpdateTicketStatusInput(null, TicketStatus.IN_PROGRESS);

        Set<ConstraintViolation<UpdateTicketStatusInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("ticketId");
    }

    @Test
    @DisplayName("status null - viola @NotNull")
    void shouldRejectNullStatus() {
        UpdateTicketStatusInput input = new UpdateTicketStatusInput(UUID.randomUUID(), null);

        Set<ConstraintViolation<UpdateTicketStatusInput>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("status");
    }
}
