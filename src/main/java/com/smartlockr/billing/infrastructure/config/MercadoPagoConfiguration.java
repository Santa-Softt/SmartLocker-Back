package com.smartlockr.billing.infrastructure.config;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.smartlockr.shared.properties.MercadoPagoProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de infraestructura para la integración con Mercado Pago.
 * Registra los clientes del SDK como Beans de Spring para permitir su inyección.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MercadoPagoConfiguration {

    private final MercadoPagoProperties properties;

    /**
     * Inicializa el SDK globalmente con el Access Token al arrancar la aplicación.
     */
    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(properties.accessToken());
        log.info("SDK de MercadoPago inicializado correctamente");
    }

    /**
     * Crea y expone el cliente para gestionar Preferencias (Checkouts).
     * @return una nueva instancia de PreferenceClient gestionada por Spring.
     */
    @Bean
    public PreferenceClient preferenceClient() {
        return new PreferenceClient();
    }

    /**
     * Crea y expone el cliente para consultar Pagos.
     * @return una nueva instancia de PaymentClient gestionada por Spring.
     */
    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient();
    }
}
