package com.smartlockr.shared.utils;

import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserConstraints {

    /**
     * Regex optimized to prevent catastrophic backtracking.
     * Supports 'Name' or 'Name Surname' with alphanumeric and Latin characters.
     */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9À-ÿ]++(?:\\s[a-zA-Z0-9À-ÿ]++)?+$");

    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "admin", "mod", "moderator", "support", "adm", "root",
            "system", "owner", "api", "internal", "security", "anonymous",
            "null", "undefined", "help", "info", "config", "bot"
    );

    /**
     * Valida el nombre de usuario bajo criterios de seguridad, longitud y palabras reservadas.
     *
     * @param name Nombre a validar
     * @throws ValidationException si el nombre no cumple los criterios.
     */
    public static void validateName(String name) {
        if (name == null)
            return;
        if (name.isBlank())
            throw new ValidationException("Full name cannot be blank");

        String trimmedName = name.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100)
            throw new ValidationException("Name must be between 2 and 100 characters.");

        if (!NAME_PATTERN.matcher(trimmedName).matches())
            throw new ValidationException(
                    "The name format is not valid. Please use only letters and numbers, " +
                            "either as a single name or 'Name Surname' (e.g., 'John Doe')."
            );

        if (FORBIDDEN_NAMES.contains(trimmedName.toLowerCase()))
            throw new ValidationException(
                    String.format("The name '%s' is reserved for system use. " +
                            "Please choose a different name to continue.", trimmedName)
            );
    }
}
