package com.smartlockr.shared.utils;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserConstraintsTest {

    @ParameterizedTest
    @NullSource
    @DisplayName("validateName - no lanza excepcion cuando el nombre es null")
    void shouldAcceptNullName(String name) {
        assertThatCode(() -> UserConstraints.validateName(name)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateName - lanza excepcion cuando el nombre es blank")
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> UserConstraints.validateName("   "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("validateName - lanza excepcion cuando el nombre es empty string")
    void shouldRejectEmptyStringName() {
        assertThatThrownBy(() -> UserConstraints.validateName(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("validateName - lanza excepcion cuando el nombre es solo whitespace/tab/newline")
    void shouldRejectWhitespaceOnlyName() {
        assertThatThrownBy(() -> UserConstraints.validateName("\t\n"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("validateName - lanza excepcion cuando el nombre tiene 1 caracter")
    void shouldRejectTooShortName() {
        assertThatThrownBy(() -> UserConstraints.validateName("A"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 2 and 100");
    }

    @Test
    @DisplayName("validateName - lanza excepcion cuando el nombre tiene mas de 100 caracteres")
    void shouldRejectTooLongName() {
        String tooLong = "a".repeat(101);
        assertThatThrownBy(() -> UserConstraints.validateName(tooLong))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 2 and 100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"John", "Jane Doe", "Maria Jose", "Al3x", "John Smith2"})
    @DisplayName("validateName - acepta nombres validos con el patron permitido")
    void shouldAcceptValidNames(String name) {
        assertThatCode(() -> UserConstraints.validateName(name)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"John_Doe", "John@Doe", "John.Doe", "John<script>", "John-Doe"})
    @DisplayName("validateName - rechaza nombres con caracteres no permitidos")
    void shouldRejectInvalidFormatNames(String name) {
        assertThatThrownBy(() -> UserConstraints.validateName(name))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("format is not valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "Admin", "mod", "moderator", "support", "root", "system",
            "owner", "api", "internal", "security", "anonymous", "null", "undefined", "help", "info",
            "config", "bot", "adm"})
    @DisplayName("validateName - rechaza nombres reservados del sistema")
    void shouldRejectForbiddenNames(String name) {
        assertThatThrownBy(() -> UserConstraints.validateName(name))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reserved for system use");
    }

    @Test
    @DisplayName("validateName - acepta nombres de exactamente 2 caracteres")
    void shouldAcceptMinLengthValidName() {
        assertThatCode(() -> UserConstraints.validateName("Jo")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateName - acepta nombres de exactamente 100 caracteres")
    void shouldAcceptMaxLengthValidName() {
        String validLong = "a".repeat(100);
        assertThatCode(() -> UserConstraints.validateName(validLong)).doesNotThrowAnyException();
    }
}
