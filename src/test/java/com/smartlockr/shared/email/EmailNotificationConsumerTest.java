package com.smartlockr.shared.email;

import com.smartlockr.shared.messaging.MessageMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class EmailNotificationConsumerTest {

    private SmtpEmailAdapter smtpEmailAdapter;
    private EmailNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        smtpEmailAdapter = mock(SmtpEmailAdapter.class);
        consumer = new EmailNotificationConsumer(smtpEmailAdapter);
    }

    @Test
    @DisplayName("onWelcomeEmailRequested - delegates valid message to SMTP adapter")
    void shouldDelegateWelcomeEmailToSmtpAdapter() {
        var email = new WelcomeEmailMessage("user@smartlockr.com", "User");
        var message = new WelcomeEmailRequestedMessage(MessageMetadata.create("corr-1"), email);

        consumer.onWelcomeEmailRequested(message);

        then(smtpEmailAdapter).should().sendWelcomeEmail(email);
    }

    @Test
    @DisplayName("onWelcomeEmailRequested - rejects invalid message without requeue")
    void shouldRejectInvalidWelcomeEmailMessageWithoutRequeue() {
        var email = new WelcomeEmailMessage(" ", "User");
        var message = new WelcomeEmailRequestedMessage(MessageMetadata.create("corr-1"), email);

        assertThatThrownBy(() -> consumer.onWelcomeEmailRequested(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
