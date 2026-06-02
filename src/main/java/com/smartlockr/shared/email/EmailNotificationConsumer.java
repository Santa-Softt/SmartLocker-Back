package com.smartlockr.shared.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final SmtpEmailAdapter smtpEmailAdapter;

    @RabbitListener(
            queues = "${smartlockr.messaging.rabbit.queues.welcome-email:smartlockr.email.welcome.q}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onWelcomeEmailRequested(WelcomeEmailRequestedMessage message) {
        if (message == null || !EmailMessageValidator.isValid(message.email())) {
            throw new AmqpRejectAndDontRequeueException("Invalid welcome email message");
        }

        smtpEmailAdapter.sendWelcomeEmail(message.email());
        log.info("Welcome email message consumed: correlationId={}", correlationId(message.metadata()));
    }

    @RabbitListener(
            queues = "${smartlockr.messaging.rabbit.queues.payment-receipt:smartlockr.email.receipt.q}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onPaymentReceiptRequested(PaymentReceiptRequestedMessage message) {
        if (message == null || !EmailMessageValidator.isValid(message.email())) {
            throw new AmqpRejectAndDontRequeueException("Invalid payment receipt email message");
        }

        smtpEmailAdapter.sendPaymentReceipt(message.email());
        log.info("Payment receipt email message consumed: correlationId={}", correlationId(message.metadata()));
    }

    private String correlationId(com.smartlockr.shared.messaging.MessageMetadata metadata) {
        return metadata == null ? "unknown" : metadata.correlationId();
    }
}
