package com.smartlockr.shared.messaging;

import com.smartlockr.shared.utils.UuidV7;

import java.time.Instant;

public record MessageMetadata(
        String messageId,
        String correlationId,
        int schemaVersion,
        Instant createdAt
) {
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public static MessageMetadata create(String correlationId) {
        String safeCorrelationId = correlationId == null || correlationId.isBlank()
                ? UuidV7.generate().toString()
                : correlationId;

        return new MessageMetadata(
                UuidV7.generate().toString(),
                safeCorrelationId,
                CURRENT_SCHEMA_VERSION,
                Instant.now()
        );
    }
}
