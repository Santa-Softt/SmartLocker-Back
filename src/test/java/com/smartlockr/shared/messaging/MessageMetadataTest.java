package com.smartlockr.shared.messaging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMetadataTest {

    @Test
    void shouldGenerateUuidV7MessageAndCorrelationIdsWhenCorrelationIdIsBlank() {
        MessageMetadata metadata = MessageMetadata.create(" ");

        assertThat(UUID.fromString(metadata.messageId()).version()).isEqualTo(7);
        assertThat(UUID.fromString(metadata.correlationId()).version()).isEqualTo(7);
    }

    @Test
    void shouldKeepProvidedCorrelationId() {
        MessageMetadata metadata = MessageMetadata.create("request-1");

        assertThat(UUID.fromString(metadata.messageId()).version()).isEqualTo(7);
        assertThat(metadata.correlationId()).isEqualTo("request-1");
    }
}
