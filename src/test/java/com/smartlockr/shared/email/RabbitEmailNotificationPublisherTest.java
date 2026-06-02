package com.smartlockr.shared.email;

import com.smartlockr.shared.properties.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class RabbitEmailNotificationPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private SmtpEmailAdapter smtpEmailAdapter;
    private RabbitEmailNotificationPublisher publisher;
    private MessagingProperties properties;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        smtpEmailAdapter = mock(SmtpEmailAdapter.class);
        properties = new MessagingProperties(true, null);
        publisher = new RabbitEmailNotificationPublisher(rabbitTemplate, properties, smtpEmailAdapter);
    }

    @Test
    @DisplayName("sendWelcomeEmail - publishes welcome email message")
    void shouldPublishWelcomeEmailMessage() {
        ArgumentCaptor<WelcomeEmailRequestedMessage> eventCaptor =
                ArgumentCaptor.forClass(WelcomeEmailRequestedMessage.class);

        publisher.sendWelcomeEmail(new WelcomeEmailMessage("user@smartlockr.com", "User"));

        then(rabbitTemplate).should().convertAndSend(
                eq(properties.rabbit().exchange()),
                eq(properties.rabbit().routingKeys().welcomeEmail()),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().email().recipientEmail()).isEqualTo("user@smartlockr.com");
        then(smtpEmailAdapter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("sendWelcomeEmail - falls back to SMTP when RabbitMQ fails")
    void shouldFallbackToSmtpWhenRabbitMqFails() {
        var message = new WelcomeEmailMessage("user@smartlockr.com", "User");
        doThrow(new AmqpException("connection refused") {
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        publisher.sendWelcomeEmail(message);

        then(smtpEmailAdapter).should().sendWelcomeEmail(message);
    }

    @Test
    @DisplayName("sendWelcomeEmail - skips invalid payload")
    void shouldSkipInvalidWelcomeEmailPayload() {
        publisher.sendWelcomeEmail(new WelcomeEmailMessage(" ", "User"));

        then(rabbitTemplate).should(never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
        then(smtpEmailAdapter).shouldHaveNoInteractions();
    }
}
