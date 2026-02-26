package com.smartlockr.shared.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Mapea las propiedades de seguridad (incluyendo JWT) desde application.properties.
 *
 * @param secretB64           La clave secreta HMAC-SHA codificada en Base64URL sin padding.
 *                            Debe representar al menos 64 bytes (512 bits) para HS512
 * @param issuer            El emisor del token (quién lo creó).
 * @param audience          El destinatario del token (para quién fue creado).
 * @param accessTtlDuration El Tiempo de Vida (Time To Live) del token de acceso.
 * @param refreshTokenByteSize El tamaño del refresh token expresado en bytes
 * @param refreshTtlDuration La duración del refresh token puede ser días, horas, etc.
 * @param oauthRedirectUri Redirección después de un inicio de sesión correcto
 * @param adminEmail El correo electronico del administrador del sistema
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @NotBlank String secretB64,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTtlDuration,
        @NotNull Duration refreshTtlDuration,
        @NotNull @Min(32) Integer refreshTokenByteSize,
        @NotBlank String oauthRedirectUri,
        @NotBlank @Email String adminEmail
) {
}
