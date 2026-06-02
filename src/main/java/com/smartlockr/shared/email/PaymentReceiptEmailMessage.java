package com.smartlockr.shared.email;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReceiptEmailMessage(
        String recipientEmail,
        String recipientName,
        UUID rentalId,
        String lockerLabel,
        String lockerSize,
        BigDecimal amountPaid,
        Instant startTime,
        Instant estimatedEndTime
) {
}
