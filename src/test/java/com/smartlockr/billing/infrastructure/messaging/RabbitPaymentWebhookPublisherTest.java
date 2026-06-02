package com.smartlockr.billing.infrastructure.messaging;

import com.smartlockr.billing.application.messaging.PaymentWebhookReceivedMessage;
import com.smartlockr.shared.messaging.MessageDispatchException;
import com.smartlockr.shared.properties.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RabbitPaymentWebhookPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitPaymentWebhookPublisher publisher;
    private MessagingProperties properties;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        properties = new MessagingProperties(true, null);
        publisher = new RabbitPaymentWebhookPublisher(rabbitTemplate, properties);
    }

    @Test
    @DisplayName("publishPaymentWebhook - publishes validated webhook message")
    void shouldPublishPaymentWebhookMessage() {
        ArgumentCaptor<PaymentWebhookReceivedMessage> messageCaptor =
                ArgumentCaptor.forClass(PaymentWebhookReceivedMessage.class);

        publisher.publishPaymentWebhook("12345", "request-1");

        then(rabbitTemplate).should().convertAndSend(
                eq(properties.rabbit().exchange()),
                eq(properties.rabbit().routingKeys().paymentWebhook()),
                messageCaptor.capture()
        );

        PaymentWebhookReceivedMessage message = messageCaptor.getValue();
        assertThat(message.paymentId()).isEqualTo("12345");
        assertThat(message.requestId()).isEqualTo("request-1");
        assertThat(message.metadata().correlationId()).isEqualTo("request-1");
        assertThat(message.metadata().schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("publishPaymentWebhook - fails closed when RabbitMQ publish fails")
    void shouldThrowDispatchExceptionWhenRabbitPublishFails() {
        doThrow(new AmqpException("connection refused") {
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatThrownBy(() -> publisher.publishPaymentWebhook("12345", "request-1"))
                .isInstanceOf(MessageDispatchException.class)
                .hasMessageContaining("could not be queued");
    }
}
