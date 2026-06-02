package com.smartlockr.billing.application.messaging;

public final class PaymentWebhookValidator {

    private static final int MAX_PAYMENT_ID_LENGTH = 20;

    private PaymentWebhookValidator() {
    }

    public static void validatePaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        if (paymentId.length() > MAX_PAYMENT_ID_LENGTH) {
            throw new IllegalArgumentException("Payment ID is too long");
        }
        if (!paymentId.matches("\\d+")) {
            throw new IllegalArgumentException("Payment ID must contain only digits");
        }
    }
}
