package com.smartlockr.shared.email;

import com.smartlockr.shared.messaging.MessageMetadata;

public record WelcomeEmailRequestedMessage(
        MessageMetadata metadata,
        WelcomeEmailMessage email
) {
}
