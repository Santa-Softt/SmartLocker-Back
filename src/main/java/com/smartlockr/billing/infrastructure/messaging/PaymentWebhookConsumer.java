package com.smartlockr.billing.infrastructure.messaging;

import com.smartlockr.billing.application.messaging.PaymentWebhookReceivedMessage;
import com.smartlockr.billing.application.messaging.PaymentWebhookValidator;
import com.smartlockr.billing.application.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookConsumer {

    private final BillingService billingService;

    @RabbitListener(
            queues = "${smartlockr.messaging.rabbit.queues.payment-webhook:smartlockr.payment.webhook.q}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onPaymentWebhookReceived(PaymentWebhookReceivedMessage message) {
        if (message == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid payment webhook message");
        }

        try {
            PaymentWebhookValidator.validatePaymentId(message.paymentId());
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Invalid payment ID in webhook message", e);
        }

        billingService.processPaymentNotification(message.paymentId());
        log.info("[MERCADOPAGO] Payment webhook consumed: paymentId={}, correlationId={}",
                message.paymentId(),
                message.metadata() == null ? "unknown" : message.metadata().correlationId());
    }
}
