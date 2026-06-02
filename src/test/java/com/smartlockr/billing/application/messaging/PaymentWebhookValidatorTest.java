package com.smartlockr.billing.application.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookValidatorTest {

    @Test
    @DisplayName("validatePaymentId - accepts numeric MercadoPago IDs")
    void shouldAcceptNumericPaymentId() {
        assertThatNoException().isThrownBy(() -> PaymentWebhookValidator.validatePaymentId("123456789"));
    }

    @Test
    @DisplayName("validatePaymentId - rejects blank IDs")
    void shouldRejectBlankPaymentId() {
        assertThatThrownBy(() -> PaymentWebhookValidator.validatePaymentId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("validatePaymentId - rejects non numeric IDs")
    void shouldRejectNonNumericPaymentId() {
        assertThatThrownBy(() -> PaymentWebhookValidator.validatePaymentId("abc-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digits");
    }
}
