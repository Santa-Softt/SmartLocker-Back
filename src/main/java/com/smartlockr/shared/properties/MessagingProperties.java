package com.smartlockr.shared.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smartlockr.messaging")
public record MessagingProperties(
        boolean emailFallbackToSmtp,
        Rabbit rabbit
) {
    public MessagingProperties {
        if (rabbit == null) {
            rabbit = new Rabbit(null, null, null, null);
        }
    }

    public record Rabbit(
            String exchange,
            String deadLetterExchange,
            QueueNames queues,
            RoutingKeys routingKeys
    ) {
        private static final String DEFAULT_EXCHANGE = "smartlockr.events";
        private static final String DEFAULT_DLX = "smartlockr.events.dlx";

        public Rabbit {
            if (exchange == null || exchange.isBlank()) {
                exchange = DEFAULT_EXCHANGE;
            }
            if (deadLetterExchange == null || deadLetterExchange.isBlank()) {
                deadLetterExchange = DEFAULT_DLX;
            }
            if (queues == null) {
                queues = new QueueNames(null, null, null);
            }
            if (routingKeys == null) {
                routingKeys = new RoutingKeys(null, null, null);
            }
        }
    }

    public record QueueNames(
            String paymentWebhook,
            String welcomeEmail,
            String paymentReceipt
    ) {
        public QueueNames {
            if (paymentWebhook == null || paymentWebhook.isBlank()) {
                paymentWebhook = "smartlockr.payment.webhook.q";
            }
            if (welcomeEmail == null || welcomeEmail.isBlank()) {
                welcomeEmail = "smartlockr.email.welcome.q";
            }
            if (paymentReceipt == null || paymentReceipt.isBlank()) {
                paymentReceipt = "smartlockr.email.receipt.q";
            }
        }
    }

    public record RoutingKeys(
            String paymentWebhook,
            String welcomeEmail,
            String paymentReceipt
    ) {
        public RoutingKeys {
            if (paymentWebhook == null || paymentWebhook.isBlank()) {
                paymentWebhook = "billing.payment.webhook.received";
            }
            if (welcomeEmail == null || welcomeEmail.isBlank()) {
                welcomeEmail = "email.welcome.requested";
            }
            if (paymentReceipt == null || paymentReceipt.isBlank()) {
                paymentReceipt = "email.receipt.requested";
            }
        }
    }
}
