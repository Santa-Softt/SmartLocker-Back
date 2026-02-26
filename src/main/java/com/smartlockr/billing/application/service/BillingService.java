package com.smartlockr.billing.application.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.billing.application.exception.RentFailedException;
import com.smartlockr.billing.infrastructure.dto.PaymentLinkResponse;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.rental.infrastructure.persistence.repository.RentalRepository;
import com.smartlockr.shared.properties.MercadoPagoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    private final RentalRepository rentalRepository;
    private final MercadoPagoProperties mpProperties;
    private final BusinessService businessService;
    private final PaymentClient paymentClient;
    private final PreferenceClient preferenceClient;
    private final StringRedisTemplate redisTemplate;
    private static final String CURRENCY_ARS = "ARS";
    private static final String PAYMENT_APPROVED = "approved";
    private static final String HOLD_KEY_PREFIX = "hold:rental:";
    private static final String ACTIVE_KEY_PREFIX = "active:rental:";

    @Transactional
    public PaymentLinkResponse createPaymentOrder(UUID rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental no encontrado: " + rentalId));

        validateRentalStatus(rental);

        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();

        Instant now = Instant.now();
        Instant originalHoldExpiration = rental.getStartTime().plusSeconds(config.holdDurationSeconds());
        long netPurchasedMinutes = Duration.between(originalHoldExpiration, rental.getEstimatedEndTime()).toMinutes();

        rental.setStartTime(now);
        rental.setEstimatedEndTime(now.plus(netPurchasedMinutes, ChronoUnit.MINUTES));

        BigDecimal establishedPrice = rental.getFinalCost();
        rentalRepository.save(rental);

        try {
            PreferenceRequest preferenceRequest = buildPreferenceRequest(rental, establishedPrice, config);
            Preference preference = preferenceClient.create(preferenceRequest);

            log.info("Preferencia de pago creada para Rental: {}. ID: {}", rentalId, preference.getId());
            return new PaymentLinkResponse(preference.getInitPoint());

        } catch (MPApiException e) {
            log.error("Error API MercadoPago [Status: {}]: {}", e.getApiResponse().getStatusCode(), e.getApiResponse().getContent());
            throw new PaymentGatewayException("Error en comunicación con pasarela de pago");
        } catch (Exception e) {
            log.error("Error crítico al generar orden de pago para Rental: {}", rentalId, e);
            throw new PaymentGatewayException("No se pudo procesar la solicitud de pago");
        }
    }

    @Transactional
    public void processPaymentNotification(String paymentId) {
        validatePaymentId(paymentId);

        String lockKey = "payment_processed:" + paymentId;
        Boolean isFirstTime = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isFirstTime)) {
            log.info("[MERCADOPAGO] Notificación {} ya en curso o finalizada.", paymentId);
            return;
        }

        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            if (PAYMENT_APPROVED.equalsIgnoreCase(payment.getStatus())) {
                processApprovedPayment(lockKey, paymentId, payment);
            } else {
                redisTemplate.delete(lockKey);
                log.info("[MERCADOPAGO] Pago {} con estado: {}. Bloqueo liberado.", paymentId, payment.getStatus());
            }

        } catch (NumberFormatException e) {
            redisTemplate.delete(lockKey);
            log.error("[MERCADOPAGO] ID de pago inválido {}: {}", paymentId, e.getMessage());
            throw new PaymentGatewayException("ID de pago inválido", e);
        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            log.error("Error crítico procesando notificación {}: {}", paymentId, e.getMessage());
            throw new PaymentGatewayException("Notification processing failed", e);
        }
    }

    /**
     * Processes a payment confirmed as approved by MercadoPago.
     * Validates the external reference, resolves the rental ID, and delegates
     * to the approval handler. Marks the lock key as processed on success.
     *
     * @param lockKey the Redis idempotency key for this payment
     * @param paymentId the MercadoPago payment ID
     * @param payment the approved payment object
     */
    private void processApprovedPayment(String lockKey, String paymentId, Payment payment) {
        String externalReference = payment.getExternalReference();
        if (externalReference == null || externalReference.isBlank()) {
            log.error("[MERCADOPAGO] Pago {} sin external_reference", paymentId);
            redisTemplate.delete(lockKey);
            return;
        }

        UUID rentalId = parseRentalId(lockKey, paymentId, externalReference);
        if (rentalId == null) {
            return;
        }

        handleApprovedPayment(rentalId, payment);
        redisTemplate.opsForValue().set(lockKey, "PROCESSED", Duration.ofHours(24));
    }

    /**
     * Parses the external reference string into a rental UUID.
     * Releases the idempotency lock and returns null if the reference is not a valid UUID.
     *
     * @param lockKey the Redis idempotency key to release on failure
     * @param paymentId the MercadoPago payment ID for logging context
     * @param externalReference the raw external reference string from MercadoPago
     * @return the parsed {@link UUID}, or null if parsing fails
     */
    private UUID parseRentalId(String lockKey, String paymentId, String externalReference) {
        try {
            return UUID.fromString(externalReference);
        } catch (IllegalArgumentException _) {
            log.error("[MERCADOPAGO] External reference inválida {}: {}", paymentId, externalReference);
            redisTemplate.delete(lockKey);
            return null;
        }
    }

    private void validatePaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new PaymentGatewayException("Payment ID es requerido");
        }
        if (paymentId.length() > 20) {
            throw new PaymentGatewayException("Payment ID demasiado largo");
        }
        if (!paymentId.matches("\\d+")) {
            throw new PaymentGatewayException("Payment ID debe contener solo dígitos");
        }
    }

    private void handleApprovedPayment(UUID rentalId, Payment payment) {
        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {

            if (rental.getState() == RentalState.ACTIVE) {
                log.warn("------------------------------------------------------------------");
                log.warn("[PAGO EXCEDENTE DETECTADO]");
                log.warn("El Rental {} ya se encuentra ACTIVE.", rentalId);
                log.warn("Payment ID: {} | Monto: {} {}", payment.getId(), payment.getTransactionAmount(), payment.getCurrencyId());
                log.warn("Acción requerida: CONCILIACIÓN MANUAL.");
                log.warn("------------------------------------------------------------------");
                return;
            }

            confirmRentalPayment(rentalId, payment.getTransactionAmount());

        }, () -> log.error("[ERROR] Pago aprobado para Rental inexistente: {}", rentalId));
    }

    private void confirmRentalPayment(UUID rentalId, BigDecimal amountPaid) {
        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {
            if (rental.getState() == RentalState.ACTIVE) {
                return;
            }

            rental.setState(RentalState.ACTIVE);
            rental.setFinalCost(amountPaid);

            Locker locker = rental.getLocker();
            locker.setState(LockerState.OCCUPIED);

            rentalRepository.save(rental);

            redisTemplate.delete(HOLD_KEY_PREFIX + rentalId);

            long rentalExpirationTTL = Duration.between(Instant.now(), rental.getEstimatedEndTime()).getSeconds();
            if (rentalExpirationTTL <= 0) rentalExpirationTTL = 1;

            redisTemplate.opsForValue().set(ACTIVE_KEY_PREFIX + rental.getId(), "1", rentalExpirationTTL, TimeUnit.SECONDS);

            log.info("Pago confirmado exitosamente para Rental: {}. Clave de expiración en Redis eliminada y timer activo iniciado.", rentalId);

        }, () -> log.error("Rental no encontrado tras notificación de pago: {}", rentalId));
    }

    private void validateRentalStatus(Rental rental) {
        if (rental.getState() != RentalState.HOLD) {
            throw new RentFailedException("No se puede realizar el alquiler con lockers en estado: " + rental.getState());
        }
    }

    private PreferenceRequest buildPreferenceRequest(Rental rental, BigDecimal amount, BusinessConfigSnapshot config) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title("Alquiler Locker - Tamaño " + rental.getLocker().getSize())
                .quantity(1)
                .unitPrice(amount)
                .currencyId(CURRENCY_ARS)
                .build();

        return PreferenceRequest.builder()
                .items(Collections.singletonList(item))
                .externalReference(rental.getId().toString())
                .notificationUrl(mpProperties.webhookUrl())
                .binaryMode(true)
                .expirationDateTo(Instant.now()
                        .plusSeconds(config.holdDurationSeconds())
                        .atOffset(ZoneOffset.UTC))
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(mpProperties.backUrlSuccess())
                        .failure(mpProperties.backUrlFailure())
                        .build())
                .autoReturn(PAYMENT_APPROVED)
                .build();
    }
}
