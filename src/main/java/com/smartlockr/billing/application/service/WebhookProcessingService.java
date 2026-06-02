package com.smartlockr.billing.application.service;

import com.smartlockr.billing.application.messaging.PaymentWebhookMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for queueing payment webhooks.
 * Decouples webhook reception from payment processing to ensure
 * fast HTTP responses to MercadoPago while maintaining reliable processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final PaymentWebhookMessagePublisher paymentWebhookMessagePublisher;

    /**
     * Processes a payment notification asynchronously in a background thread.
     * Errors are caught and logged to prevent thread pool exhaustion.
     *
     * @param paymentId the MercadoPago payment identifier to process
     */
    public void processPaymentAsync(String paymentId) {
        processPaymentAsync(paymentId, null);
    }

    public void processPaymentAsync(String paymentId, String requestId) {
        paymentWebhookMessagePublisher.publishPaymentWebhook(paymentId, requestId);
        log.info("[MERCADOPAGO] Payment webhook accepted for queueing: paymentId={}", paymentId);
    }
}
