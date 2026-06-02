package com.smartlockr.shared.email;

import com.smartlockr.shared.properties.EmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailAdapter {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public void sendWelcomeEmail(WelcomeEmailMessage message) {
        if (shouldSkip(message.recipientEmail())) {
            return;
        }

        SimpleMailMessage mail = baseMessage(message.recipientEmail());
        mail.setSubject("Bienvenido a " + properties.brandName());
        mail.setText("""
                Hola %s,

                Bienvenido a %s. Tu cuenta ya esta lista para alquilar lockers de forma segura.

                Si necesitas ayuda, escribinos a %s.
                """.formatted(displayName(message.recipientName()), properties.brandName(), properties.supportAddress()));

        send(mail, "welcome");
    }

    public void sendPaymentReceipt(PaymentReceiptEmailMessage message) {
        if (shouldSkip(message.recipientEmail())) {
            return;
        }

        SimpleMailMessage mail = baseMessage(message.recipientEmail());
        mail.setSubject("Recibo de pago - " + properties.brandName());
        mail.setText("""
                Hola %s,

                Recibimos tu pago correctamente.

                Alquiler: %s
                Locker: %s (%s)
                Importe: ARS %s
                Inicio: %s
                Fin estimado: %s

                Gracias por usar %s.
                """.formatted(
                displayName(message.recipientName()),
                message.rentalId(),
                safeText(message.lockerLabel()),
                safeText(message.lockerSize()),
                formatAmount(message.amountPaid()),
                formatInstant(message.startTime()),
                formatInstant(message.estimatedEndTime()),
                properties.brandName()
        ));

        send(mail, "payment receipt");
    }

    private SimpleMailMessage baseMessage(String recipientEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setReplyTo(properties.supportAddress());
        message.setTo(recipientEmail);
        return message;
    }

    private boolean shouldSkip(String recipientEmail) {
        return !properties.enabled() || recipientEmail == null || recipientEmail.isBlank();
    }

    private void send(SimpleMailMessage message, String notificationType) {
        try {
            mailSender.send(message);
            log.info("Email notification sent: type={}", notificationType);
        } catch (MailException e) {
            log.warn("Email notification failed: type={}, reason={}", notificationType, e.getMessage());
            throw new EmailDeliveryException("Email notification failed: " + notificationType, e);
        }
    }

    private String displayName(String value) {
        return value == null || value.isBlank() ? "Usuario" : value;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
