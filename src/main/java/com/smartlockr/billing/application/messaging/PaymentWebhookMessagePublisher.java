package com.smartlockr.billing.application.messaging;

public interface PaymentWebhookMessagePublisher {
    void publishPaymentWebhook(String paymentId, String requestId);
}
