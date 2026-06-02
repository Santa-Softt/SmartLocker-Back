package com.smartlockr.billing.application.messaging;

import com.smartlockr.shared.messaging.MessageMetadata;

public record PaymentWebhookReceivedMessage(
        MessageMetadata metadata,
        String paymentId,
        String requestId
) {
}
