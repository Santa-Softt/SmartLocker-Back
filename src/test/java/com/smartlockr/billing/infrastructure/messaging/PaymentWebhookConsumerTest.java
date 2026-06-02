package com.smartlockr.billing.infrastructure.messaging;

import com.smartlockr.billing.application.messaging.PaymentWebhookReceivedMessage;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.billing.application.service.BillingService;
import com.smartlockr.shared.messaging.MessageMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PaymentWebhookConsumerTest {

    private BillingService billingService;
    private PaymentWebhookConsumer consumer;

    @BeforeEach
    void setUp() {
        billingService = mock(BillingService.class);
        consumer = new PaymentWebhookConsumer(billingService);
    }

    @Test
    @DisplayName("onPaymentWebhookReceived - delegates valid payment IDs to BillingService")
    void shouldDelegateValidPaymentIdToBillingService() {
        var message = new PaymentWebhookReceivedMessage(MessageMetadata.create("request-1"), "12345", "request-1");

        consumer.onPaymentWebhookReceived(message);

        then(billingService).should().processPaymentNotification("12345");
    }

    @Test
    @DisplayName("onPaymentWebhookReceived - rejects invalid payload without requeue")
    void shouldRejectInvalidPayloadWithoutRequeue() {
        var message = new PaymentWebhookReceivedMessage(MessageMetadata.create("request-1"), "abc", "request-1");

        assertThatThrownBy(() -> consumer.onPaymentWebhookReceived(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    @DisplayName("onPaymentWebhookReceived - propagates gateway failures for RabbitMQ retry")
    void shouldPropagateGatewayFailuresForRetry() {
        var message = new PaymentWebhookReceivedMessage(MessageMetadata.create("request-1"), "12345", "request-1");
        doThrow(new PaymentGatewayException("MercadoPago unavailable"))
                .when(billingService)
                .processPaymentNotification("12345");

        assertThatThrownBy(() -> consumer.onPaymentWebhookReceived(message))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("MercadoPago unavailable");
    }
}
