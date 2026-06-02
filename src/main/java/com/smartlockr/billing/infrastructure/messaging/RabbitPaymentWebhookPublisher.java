package com.smartlockr.billing.infrastructure.messaging;

import com.smartlockr.billing.application.messaging.PaymentWebhookMessagePublisher;
import com.smartlockr.billing.application.messaging.PaymentWebhookReceivedMessage;
import com.smartlockr.billing.application.messaging.PaymentWebhookValidator;
import com.smartlockr.shared.messaging.MessageDispatchException;
import com.smartlockr.shared.messaging.MessageMetadata;
import com.smartlockr.shared.properties.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitPaymentWebhookPublisher implements PaymentWebhookMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @Override
    public void publishPaymentWebhook(String paymentId, String requestId) {
        PaymentWebhookValidator.validatePaymentId(paymentId);

        PaymentWebhookReceivedMessage message = new PaymentWebhookReceivedMessage(
                MessageMetadata.create(requestId),
                paymentId,
                requestId
        );

        try {
            rabbitTemplate.convertAndSend(
                    properties.rabbit().exchange(),
                    properties.rabbit().routingKeys().paymentWebhook(),
                    message
            );
            log.info("[MERCADOPAGO] Payment webhook queued: paymentId={}, correlationId={}",
                    paymentId, message.metadata().correlationId());
        } catch (AmqpException e) {
            throw new MessageDispatchException("Payment webhook could not be queued", e);
        }
    }
}
