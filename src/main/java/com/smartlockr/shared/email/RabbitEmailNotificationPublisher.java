package com.smartlockr.shared.email;

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
public class RabbitEmailNotificationPublisher implements EmailNotificationSender {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final SmtpEmailAdapter smtpEmailAdapter;

    @Override
    public void sendWelcomeEmail(WelcomeEmailMessage message) {
        if (!EmailMessageValidator.isValid(message)) {
            log.warn("Welcome email request skipped because payload is invalid");
            return;
        }

        WelcomeEmailRequestedMessage event = new WelcomeEmailRequestedMessage(
                MessageMetadata.create(null),
                message
        );

        publishOrFallback(
                properties.rabbit().routingKeys().welcomeEmail(),
                event,
                () -> smtpEmailAdapter.sendWelcomeEmail(message),
                "welcome"
        );
    }

    @Override
    public void sendPaymentReceipt(PaymentReceiptEmailMessage message) {
        if (!EmailMessageValidator.isValid(message)) {
            log.warn("Payment receipt email request skipped because payload is invalid");
            return;
        }

        PaymentReceiptRequestedMessage event = new PaymentReceiptRequestedMessage(
                MessageMetadata.create(message.rentalId().toString()),
                message
        );

        publishOrFallback(
                properties.rabbit().routingKeys().paymentReceipt(),
                event,
                () -> smtpEmailAdapter.sendPaymentReceipt(message),
                "payment receipt"
        );
    }

    private void publishOrFallback(String routingKey, Object event, Runnable fallback, String notificationType) {
        try {
            rabbitTemplate.convertAndSend(properties.rabbit().exchange(), routingKey, event);
            log.info("Email notification queued: type={}", notificationType);
        } catch (AmqpException e) {
            log.warn("Email notification queue failed: type={}, reason={}", notificationType, e.getMessage());
            if (properties.emailFallbackToSmtp()) {
                try {
                    fallback.run();
                    log.info("Email notification fallback completed: type={}", notificationType);
                } catch (RuntimeException fallbackException) {
                    log.warn("Email notification fallback failed: type={}, reason={}",
                            notificationType, fallbackException.getMessage());
                }
            }
        }
    }
}
