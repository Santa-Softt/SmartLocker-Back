package com.smartlockr.rental.infrastructure.redis;

import com.smartlockr.rental.application.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalExpirationListener {

    private final RentalService rentalService;

    private static final String HOLD_KEY_PREFIX = "hold:rental:";
    private static final String ACTIVE_KEY_PREFIX = "active:rental:";

    @EventListener
    public void handleRedisKeyExpired(RedisKeyExpiredEvent<String> event) {
        Object source = event.getSource();

        String expiredKey = new String((byte[]) source, StandardCharsets.UTF_8);

        expiredKey = expiredKey.replace("\"", "");

        try {
            if (expiredKey.startsWith(HOLD_KEY_PREFIX)) {
                handleHoldExpiration(expiredKey);
            } else if (expiredKey.startsWith(ACTIVE_KEY_PREFIX)) {
                handleActiveExpiration(expiredKey);
            }
        } catch (Exception e) {
            log.error("[REDIS] Error general procesando el evento de expiración para clave: {}", expiredKey, e);
        }
    }

    private void handleHoldExpiration(String key) {
        String rentalIdStr = key.replace(HOLD_KEY_PREFIX, "").trim();

        try {
            UUID rentalId = UUID.fromString(rentalIdStr);
            log.info("[REDIS] HOLD expirado. Invocando limpieza para Rental: {}", rentalId);
            rentalService.expireSystemHold(rentalId);
        } catch (IllegalArgumentException _) {
            log.error("[REDIS] La clave HOLD expirada no contiene un UUID válido. Clave original: '{}', UUID extraído: '{}'", key, rentalIdStr);
        }
    }

    private void handleActiveExpiration(String key) {
        // 1. Extraer el UUID
        String rentalIdStr = key.replace(ACTIVE_KEY_PREFIX, "").trim();

        try {
            UUID rentalId = UUID.fromString(rentalIdStr);

            log.info("[REDIS] Alquiler ACTIVE expirado. Invocando penalización para Rental: {}", rentalId);
            rentalService.applyPenaltyToRental(rentalId);

        } catch (IllegalArgumentException _) {
            log.error("[REDIS] La clave ACTIVE expirada no contiene un UUID válido. Clave original: '{}', UUID extraído: '{}'", key, rentalIdStr);
        }
    }
}
