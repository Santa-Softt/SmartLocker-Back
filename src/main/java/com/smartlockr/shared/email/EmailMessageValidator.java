package com.smartlockr.shared.email;

import java.math.BigDecimal;

public final class EmailMessageValidator {

    private EmailMessageValidator() {
    }

    public static boolean isValid(WelcomeEmailMessage message) {
        return message != null && hasText(message.recipientEmail());
    }

    public static boolean isValid(PaymentReceiptEmailMessage message) {
        return message != null
                && hasText(message.recipientEmail())
                && message.rentalId() != null
                && isNonNegative(message.amountPaid());
    }

    private static boolean isNonNegative(BigDecimal amount) {
        return amount == null || amount.signum() >= 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
