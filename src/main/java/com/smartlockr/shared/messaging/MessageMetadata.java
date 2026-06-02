package com.smartlockr.shared.messaging;

import java.time.Instant;
import java.util.UUID;

public record MessageMetadata(
        String messageId,
        String correlationId,
        int schemaVersion,
        Instant createdAt
) {
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public static MessageMetadata create(String correlationId) {
        String safeCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;

        return new MessageMetadata(
                UUID.randomUUID().toString(),
                safeCorrelationId,
                CURRENT_SCHEMA_VERSION,
                Instant.now()
        );
    }
}
