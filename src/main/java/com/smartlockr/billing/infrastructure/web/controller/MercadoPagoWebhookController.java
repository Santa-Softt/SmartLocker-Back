package com.smartlockr.billing.infrastructure.web.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartlockr.billing.application.service.MercadoPagoSignatureValidator;
import com.smartlockr.billing.application.service.WebhookProcessingService;
import com.smartlockr.shared.messaging.MessageDispatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling MercadoPago webhook notifications.
 * Processes payment events asynchronously to ensure fast response times.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final WebhookProcessingService webhookProcessingService;
    private final MercadoPagoSignatureValidator signatureValidator;

    /**
     * Receives and processes payment webhook notifications from MercadoPago.
     * Validates signature when headers are present, then queues payment for
     * async processing to avoid blocking the HTTP response.
     *
     * @param signature optional webhook signature for verification
     * @param requestId optional request ID for tracing
     * @param payload the webhook payload containing payment information
     * @return 200 OK if queued successfully, 400 if payload invalid, 503 if queue unavailable
     */
    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestBody WebhookPayload payload) {

        try {
            if (payload == null || payload.type() == null) {
                log.warn("[MERCADOPAGO] Invalid webhook payload");
                return ResponseEntity.badRequest().build();
            }

            if (!"payment".equalsIgnoreCase(payload.type())) {
                return ResponseEntity.ok().build();
            }

            if (payload.data() == null || payload.data().id() == null) {
                log.warn("[MERCADOPAGO] Payment ID missing in payload");
                return ResponseEntity.badRequest().build();
            }

            String resourceId = payload.data().id();

            if (signature != null && requestId != null && !signatureValidator.isValid(signature, requestId, resourceId)) {
                log.warn("[MERCADOPAGO] Invalid signature for resource: {}", resourceId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            webhookProcessingService.processPaymentAsync(resourceId, requestId);
            return ResponseEntity.ok().build();

        } catch (MessageDispatchException e) {
            log.error("[MERCADOPAGO] Webhook could not be queued. MercadoPago should retry. reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (IllegalArgumentException e) {
            log.warn("[MERCADOPAGO] Invalid webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[MERCADOPAGO] Unexpected error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Receives webhook notifications without signature verification.
     * Intended for development or testing environments.
     *
     * @param payload the webhook payload containing payment information
     * @return 200 OK if queued successfully, 500 on error
     */
    @PostMapping("/none")
    public ResponseEntity<Void> receiveWebhookNoSignature(@RequestBody WebhookPayload payload) {
        try {
            if (payload == null || payload.type() == null || !"payment".equalsIgnoreCase(payload.type())) {
                return ResponseEntity.ok().build();
            }

            String paymentId = payload.data() != null ? payload.data().id() : null;
            if (paymentId != null) {
                webhookProcessingService.processPaymentAsync(paymentId);
            }
            return ResponseEntity.ok().build();

        } catch (MessageDispatchException e) {
            log.error("[MERCADOPAGO] Webhook could not be queued in no-signature endpoint. reason={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (IllegalArgumentException e) {
            log.warn("[MERCADOPAGO] Invalid no-signature webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[MERCADOPAGO] Unexpected error processing webhook (no-signature endpoint)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DTO for MercadoPago webhook payload.
     * Ignores unknown properties to maintain compatibility with API changes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookPayload(
            String id,
            String type,
            String action,
            Data data
    ) {
        /**
         * Nested DTO for webhook data containing the resource identifier.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Data(String id) {
        }
    }
}
