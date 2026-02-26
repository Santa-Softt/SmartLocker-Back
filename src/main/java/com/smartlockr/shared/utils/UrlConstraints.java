package com.smartlockr.shared.utils;

import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Utility class for URL format and protocol constraints.
 * Centralizes web link validation for the SmartLockr platform.
 * Includes SSRF (Server-Side Request Forgery) prevention.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UrlConstraints {

    /**
     * Optimized regex for URL validation.
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?|ftp)++://[\\w\\-.]++(?:\\.[\\w\\-.]++)*+(?::\\d++)?+(?:/\\S++)?+$");

    /**
     * Maximum allowed URL length to prevent buffer overflow attacks.
     */
    private static final int MAX_URL_LENGTH = 2048;

    /**
     * Allowed protocols for URLs (prevents javascript:, data:, file: schemes).
     */
    private static final String[] ALLOWED_PROTOCOLS = {"http", "https", "ftp"};

    /**
     * Validates if the provided string is a well-formed web URL.
     * Includes SSRF prevention by blocking private IP addresses.
     *
     * @param url The URL string to validate.
     * @throws ValidationException if the URL is blank, fails structural format checks, 
     *                             or points to a private/internal IP address.
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

        if (trimmedUrl.length() > MAX_URL_LENGTH)
            throw new ValidationException(
                    "The URL is too long. Please provide a link shorter than 2048 characters."
            );

        validateProtocol(trimmedUrl);
        validateNotPrivateIp(trimmedUrl);
    }

    /**
     * Validates that the URL uses an allowed protocol.
     * Prevents dangerous schemes like javascript:, data:, file:.
     */
    private static void validateProtocol(String url) {
        try {
            URI parsedUri = new URI(url);
            String scheme = parsedUri.getScheme();

            if (scheme == null) {
                throw new ValidationException("URL sin protocolo definido.");
            }

            String protocol = scheme.toLowerCase();
            for (String allowed : ALLOWED_PROTOCOLS) {
                if (allowed.equals(protocol)) {
                    return;
                }
            }
            throw new ValidationException(
                    "Protocolo no permitido: " + protocol + ". Solo se permiten: http, https, ftp."
            );
        } catch (URISyntaxException e) {
            throw new ValidationException("URL inválida: " + e.getMessage());
        }
    }

    /**
     * Validates that the URL does not point to a private or internal IP address.
     * Prevents SSRF (Server-Side Request Forgery) attacks.
     */
    private static void validateNotPrivateIp(String url) {
        try {
            var host = getString(url);

            // Block private IP ranges
            if (isPrivateIp(host)) {
                throw new ValidationException(
                        "No se permiten URLs que apunten a direcciones IP privadas."
                );
            }

        } catch (ValidationException e) {
            throw e;
        } catch (Exception _) {
            // If we can't resolve the host, allow it (will fail on connection anyway)
            // This prevents blocking legitimate URLs that DNS can't resolve at validation time
        }
    }

    private static @NonNull String getString(String url) throws URISyntaxException {
        var parsedUri = new URI(url);
        String host = parsedUri.getHost();

        if (host == null || host.isBlank()) {
            throw new ValidationException("URL sin host válido");
        }

        if ("localhost".equalsIgnoreCase(host) ||
                "127.0.0.1".equals(host) ||
                "::1".equals(host) ||
                "0.0.0.0".equals(host)) {
            throw new ValidationException(
                    "No se permiten URLs que apunten a localhost o direcciones internas."
            );
        }
        return host;
    }

    /**
     * Checks if a hostname or IP address is in a private range.
     */
    private static boolean isPrivateIp(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress() ||
                   address.isAnyLocalAddress() ||
                   address.isLinkLocalAddress() ||
                   address.isSiteLocalAddress();
        } catch (Exception _) {
            return false;
        }
    }
}
