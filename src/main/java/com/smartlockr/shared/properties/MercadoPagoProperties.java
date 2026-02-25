package com.smartlockr.shared.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de configuración para la integración con Mercado Pago.
 * Mapea los valores definidos en el archivo application.yml bajo el prefijo 'mercadopago'.
 *
 * @param accessToken    Token de acceso para la API (Producción o Sandbox).
 * @param webhookSecret  Clave secreta para validar la firma HMAC de las notificaciones (IPN).
 * @param webhookUrl     URL pública de nuestra API donde MP enviará las notificaciones.
 * @param backUrlSuccess URL del frontend para redirigir al usuario tras un pago exitoso.
 * @param backUrlFailure URL del frontend para redirigir al usuario tras un pago fallido.
 */
@Validated
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
        @NotBlank String accessToken,
        @NotBlank String webhookSecret,
        @NotBlank String webhookUrl,
        @NotBlank String backUrlSuccess,
        @NotBlank String backUrlFailure
) {}
