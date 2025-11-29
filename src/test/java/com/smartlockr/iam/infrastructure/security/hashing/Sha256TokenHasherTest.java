package com.smartlockr.iam.infrastructure.security.hashing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


class Sha256TokenHasherTest {

    private Sha256TokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        // Al no tener dependencias, instanciamos directamente.
        // Cero overhead, ejecución en nanosegundos.
        tokenHasher = new Sha256TokenHasher();
    }

    @Test
    @DisplayName("Hashing: Should be deterministic (Same Input -> Same Output)")
    void hash_ShouldBeDeterministic() {
        String input = "secure-password-123";

        String hash1 = tokenHasher.hash(input);
        String hash2 = tokenHasher.hash(input);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Algorithm: Should match standard SHA-256 Known Vector")
    void hash_ShouldMatchKnownVector() {
        String input = "hello";
        String expectedHash = "LPJNul-wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ";

        String actualHash = tokenHasher.hash(input);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("Encoding: Should be URL-Safe and Padding-Free")
    void hash_ShouldUseUrlSafeEncoding() {
        // Generamos un input que sabemos produce caracteres + o / en Base64 estándar
        // Para asegurar que el encoder los reemplaza por - y _
        String input = "testing-encoding-safety";

        String result = tokenHasher.hash(input);

        assertThat(result)
                .doesNotContain("+") // No standard Base64
                .doesNotContain("/") // No standard Base64
                .doesNotContain("=") // No padding
                .matches("^[A-Za-z0-9\\-_]+$"); // Alfabeto Base64URL
    }

    @ParameterizedTest(name = "Matches: raw={0}, expected={1}")
    @CsvSource({
            "password123, true",
            "wrongpassword, false",
            ", false",
            "'', false"
    })
    @DisplayName("Matches: Should verify integrity correctly")
    void matches_ShouldVerifyHash(String attempt, boolean expectedResult) {
        String originalSecret = "password123";
        String validHash = tokenHasher.hash(originalSecret);

        if (attempt == null) {
            assertThatThrownBy(() -> tokenHasher.matches(null, validHash))
                    .isInstanceOf(NullPointerException.class);
        } else {
            boolean result = tokenHasher.matches(attempt, validHash);
            assertThat(result).isEqualTo(expectedResult);
        }
    }

    @Test
    @DisplayName("Matches: Should fail efficiently on tampered hash")
    void matches_TamperedHash_ShouldReturnFalse() {
        String raw = "secret";
        String validHash = tokenHasher.hash(raw);
        // Modificamos el último caracter del hash
        String tamperedHash = validHash.substring(0, validHash.length() - 1) + "0";

        boolean result = tokenHasher.matches(raw, tamperedHash);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Edge Case: Null Input should throw NPE (Fail Fast)")
    void hash_NullInput_ShouldThrow() {
        assertThatThrownBy(() -> tokenHasher.hash(null))
                .isInstanceOf(NullPointerException.class);
    }
}