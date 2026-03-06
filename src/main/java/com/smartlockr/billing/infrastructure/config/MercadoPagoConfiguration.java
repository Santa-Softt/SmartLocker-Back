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

import java.util.concurrent.TimeUnit;

/**
 * Configuration for MercadoPago SDK integration.
 * Initializes the SDK with access token and configures HTTP timeouts
 * to prevent connection hangs during payment operations.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MercadoPagoConfiguration {

    private final MercadoPagoProperties properties;

    /**
     * Initializes MercadoPago SDK with access token and connection timeouts.
     * Connection timeout: 10 seconds for establishing connections.
     * Socket timeout: 30 seconds for waiting on data during requests.
     */
    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(properties.accessToken());
        MercadoPagoConfig.setConnectionTimeout((int) TimeUnit.SECONDS.toMillis(10));
        MercadoPagoConfig.setSocketTimeout((int) TimeUnit.SECONDS.toMillis(30));
        log.info("MercadoPago SDK initialized with timeouts: connection=10s, socket=30s");
    }

    /**
     * Creates a PreferenceClient bean for managing checkout preferences.
     *
     * @return a new PreferenceClient instance
     */
    @Bean
    public PreferenceClient preferenceClient() {
        return new PreferenceClient();
    }

    /**
     * Creates a PaymentClient bean for querying payment information.
     *
     * @return a new PaymentClient instance
     */
    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient();
    }
}
