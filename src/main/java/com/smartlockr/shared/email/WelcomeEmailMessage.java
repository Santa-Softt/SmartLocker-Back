package com.smartlockr.shared.email;

public record WelcomeEmailMessage(
        String recipientEmail,
        String recipientName
) {
}
