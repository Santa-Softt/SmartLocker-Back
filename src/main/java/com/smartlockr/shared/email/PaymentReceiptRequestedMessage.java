package com.smartlockr.shared.email;

import com.smartlockr.shared.messaging.MessageMetadata;

public record PaymentReceiptRequestedMessage(
        MessageMetadata metadata,
        PaymentReceiptEmailMessage email
) {
}
