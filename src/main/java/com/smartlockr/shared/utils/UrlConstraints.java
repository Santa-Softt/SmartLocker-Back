package com.smartlockr.shared.utils;

import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * Utility class for URL format and protocol constraints.
 * Centralizes web link validation for the SmartLockr platform.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UrlConstraints {

    /**
     * Optimized regex for URL validation.
     * ^(https?|ftp)++ : Protocol group (http, https, or ftp) with possessive quantifier.
     * ://[^\s/$.?#].[^\s]*+$ : Host and path consumption without backtracking.
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?|ftp)++://[\\w\\-.]++(?:\\.[\\w\\-.]++)*+(?::\\d++)?+(?:/\\S++)?+$");

    /**
     * Validates if the provided string is a well-formed web URL.
     *
     * @param url The URL string to validate.
     * @throws ValidationException if the URL is blank or fails structural format checks.
     */
    public static void validateUrl(String url) {
        if (url == null)
            return;
        if (url.isBlank())
            throw new ValidationException(
                    "Please provide a valid URL; this field cannot be left blank."
            );

        String trimmedUrl = url.trim();

        if (!URL_PATTERN.matcher(trimmedUrl).matches())
            throw new ValidationException(
                    "The URL format is invalid. Please ensure it starts with a valid protocol " +
                            "(e.g., 'https://') and follows a standard web address format (e.g., 'https://example.com/avatar.png')."
            );

        if (trimmedUrl.length() > 2048)
            throw new ValidationException(
                    "The URL is too long. Please provide a link shorter than 2048 characters."
            );
    }
}
