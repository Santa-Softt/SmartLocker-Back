package com.smartlockr.rental.infrastructure.redis;

import com.smartlockr.rental.application.service.RentalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RentalExpirationListenerTest {

    @Test
    @DisplayName("handleRedisKeyExpired - expires hold rentals")
    void shouldExpireHoldRental() {
        RentalService rentalService = mock(RentalService.class);
        RentalExpirationListener listener = new RentalExpirationListener(rentalService);
        UUID rentalId = UUID.randomUUID();

        listener.handleRedisKeyExpired(event("hold:rental:" + rentalId));

        then(rentalService).should().expireSystemHold(rentalId);
    }

    @Test
    @DisplayName("handleRedisKeyExpired - applies penalties to active rentals")
    void shouldApplyPenaltyToActiveRental() {
        RentalService rentalService = mock(RentalService.class);
        RentalExpirationListener listener = new RentalExpirationListener(rentalService);
        UUID rentalId = UUID.randomUUID();

        listener.handleRedisKeyExpired(event("\"active:rental:" + rentalId + "\""));

        then(rentalService).should().applyPenaltyToRental(rentalId);
    }

    @Test
    @DisplayName("handleRedisKeyExpired - ignores invalid keys defensively")
    void shouldIgnoreInvalidKeys() {
        RentalService rentalService = mock(RentalService.class);
        RentalExpirationListener listener = new RentalExpirationListener(rentalService);

        listener.handleRedisKeyExpired(event("hold:rental:not-a-uuid"));
        listener.handleRedisKeyExpired(event("other:key"));

        verifyNoInteractions(rentalService);
    }

    private RedisKeyExpiredEvent<String> event(String key) {
        return new RedisKeyExpiredEvent<>(key.getBytes(StandardCharsets.UTF_8));
    }
}
