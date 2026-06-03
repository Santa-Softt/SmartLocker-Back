package com.smartlockr.shared.email;

import com.smartlockr.shared.properties.EmailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SmtpEmailAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    private SmtpEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SmtpEmailAdapter(
                mailSender,
                new EmailProperties(true, "no-reply@test.local", "support@test.local", "SmartLockr")
        );
    }

    @Test
    @DisplayName("sendWelcomeEmail - sends a complete welcome email")
    void shouldSendWelcomeEmail() {
        adapter.sendWelcomeEmail(new WelcomeEmailMessage("user@test.local", "Alex"));

        then(mailSender).should().send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly("user@test.local");
        assertThat(message.getFrom()).isEqualTo("no-reply@test.local");
        assertThat(message.getReplyTo()).isEqualTo("support@test.local");
        assertThat(message.getSubject()).contains("SmartLockr");
        assertThat(message.getText()).contains("Alex", "support@test.local");
    }

    @Test
    @DisplayName("sendPaymentReceipt - uses safe defaults for missing receipt fields")
    void shouldSendPaymentReceiptWithSafeDefaults() {
        adapter.sendPaymentReceipt(new PaymentReceiptEmailMessage(
                "user@test.local",
                "",
                com.smartlockr.shared.utils.UuidV7.generate(),
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-02T13:00:00Z")
        ));

        then(mailSender).should().send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getSubject()).contains("Recibo");
        assertThat(message.getText()).contains("Usuario", "ARS 0", "Locker: - (-)", "Fin estimado: 2026-06-02T13:00:00Z");
    }

    @Test
    @DisplayName("sendPaymentReceipt - formats amount and timestamps")
    void shouldFormatReceiptFields() {
        UUID rentalId = com.smartlockr.shared.utils.UuidV7.generate();

        adapter.sendPaymentReceipt(new PaymentReceiptEmailMessage(
                "user@test.local",
                "Alex",
                rentalId,
                "M-01",
                "M",
                BigDecimal.valueOf(123.45),
                Instant.parse("2026-06-02T12:00:00Z"),
                Instant.parse("2026-06-02T13:00:00Z")
        ));

        then(mailSender).should().send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getText())
                .contains(rentalId.toString(), "M-01 (M)", "ARS 123.45", "2026-06-02T12:00:00Z");
    }

    @Test
    @DisplayName("sendWelcomeEmail - skips blank recipients")
    void shouldSkipBlankRecipient() {
        adapter.sendWelcomeEmail(new WelcomeEmailMessage(" ", "Alex"));

        then(mailSender).should(never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendWelcomeEmail - wraps SMTP failures")
    void shouldWrapMailException() {
        doThrow(new MailException("smtp down") { })
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatThrownBy(() -> adapter.sendWelcomeEmail(new WelcomeEmailMessage("user@test.local", null)))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("welcome");
    }
}
