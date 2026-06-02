package com.smartlockr.shared.email;

public interface EmailNotificationSender {

    void sendWelcomeEmail(WelcomeEmailMessage message);

    void sendPaymentReceipt(PaymentReceiptEmailMessage message);
}
