package com.smartlockr.shared.messaging;

public class MessageDispatchException extends RuntimeException {
    public MessageDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
