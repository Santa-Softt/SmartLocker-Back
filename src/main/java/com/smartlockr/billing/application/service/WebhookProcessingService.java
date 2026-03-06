package com.smartlockr.billing.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for asynchronous processing of payment webhooks.
 * Decouples webhook reception from payment processing to ensure
 * fast HTTP responses to MercadoPago while maintaining reliable processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final BillingService billingService;

    /**
     * Processes a payment notification asynchronously in a background thread.
     * Errors are caught and logged to prevent thread pool exhaustion.
     *
     * @param paymentId the MercadoPago payment identifier to process
     */
    @Async("webhookExecutor")
    public void processPaymentAsync(String paymentId) {
        long startTime = System.currentTimeMillis();
        try {
            billingService.processPaymentNotification(paymentId);
            log.info("[MERCADOPAGO] Payment {} processed in {}ms", paymentId, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[MERCADOPAGO] Payment {} failed in {}ms: {}", paymentId, System.currentTimeMillis() - startTime, e.getMessage());
        }
    }
}
