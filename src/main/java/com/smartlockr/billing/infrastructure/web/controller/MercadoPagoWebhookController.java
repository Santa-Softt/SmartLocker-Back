package com.smartlockr.billing.infrastructure.web.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.billing.application.service.MercadoPagoSignatureValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling incoming Mercado Pago Webhook notifications.
 *
 * @author fr4ncisx
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final BillingService billingService;
    private final MercadoPagoSignatureValidator signatureValidator;

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader("x-signature") String signature,
            @RequestHeader("x-request-id") String requestId,
            @RequestBody WebhookPayload payload) {

        if (!"payment".equalsIgnoreCase(payload.type())) {
            return ResponseEntity.ok().build();
        }

        String resourceId = payload.data().id();

        if (!signatureValidator.isValid(signature, requestId, resourceId)) {
            log.warn("[MERCADOPAGO] Rejected unauthorized webhook for resource: {}", resourceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("[MERCADOPAGO] Signature verified. Processing payment: {}", resourceId);
        billingService.processPaymentNotification(resourceId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/none")
    public ResponseEntity<Void> receiveWebhook(@RequestBody WebhookPayload payload) {

        if (!"payment".equalsIgnoreCase(payload.type())) {
            return ResponseEntity.status(HttpStatus.OK).build();
        }

        String paymentId = payload.data().id();
        billingService.processPaymentNotification(paymentId);
        return ResponseEntity.ok().build();
    }

    /**
     * DTO for the webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookPayload(
            String id,
            String type,
            String action,
            Data data
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Data(String id) {
        }
    }
}
