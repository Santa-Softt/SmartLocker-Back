package com.smartlockr.shared.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7ValueGeneratorTest {

    @Test
    void shouldGenerateUuidV7WithRfcVariant() {
        UUID uuid = new UuidV7ValueGenerator().generateUuid(null);

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }
}
