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
import com.smartlockr.billing.infrastructure.graphql.dto.PaymentLinkResponse;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.BusinessConfig;
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

    @Transactional
    public PaymentLinkResponse createPaymentOrder(UUID rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental no encontrado: " + rentalId));

        validateRentalStatus(rental);
        var config = businessService.getActiveBusinessConfig();

        Instant now = Instant.now();
        Instant originalHoldExpiration = rental.getStartTime().plusSeconds(config.getHoldDurationSeconds());
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
                UUID rentalId = UUID.fromString(payment.getExternalReference());

                handleApprovedPayment(rentalId, payment);

                redisTemplate.opsForValue().set(lockKey, "PROCESSED", Duration.ofHours(24));
            } else {
                redisTemplate.delete(lockKey);
                log.info("[MERCADOPAGO] Pago {} con estado: {}. Bloqueo liberado.", paymentId, payment.getStatus());
            }

        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            log.error("Error crítico procesando notificación {}: {}", paymentId, e.getMessage());
            throw new PaymentGatewayException("Notification processing failed", e);
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

            long secondsUntilExpiration = Duration.between(Instant.now(), rental.getEstimatedEndTime()).getSeconds();
            if (secondsUntilExpiration <= 0) secondsUntilExpiration = 1;

            redisTemplate.opsForValue().set("active:rental:" + rental.getId(), "1", secondsUntilExpiration, TimeUnit.SECONDS);

            log.info("Pago confirmado exitosamente para Rental: {}. Clave de expiración en Redis eliminada y timer activo iniciado.", rentalId);

        }, () -> log.error("Rental no encontrado tras notificación de pago: {}", rentalId));
    }

    private void validateRentalStatus(Rental rental) {
        if (rental.getState() != RentalState.HOLD) {
            throw new RentFailedException("No se puede realizar el alquiler con lockers en estado: " + rental.getState());
        }
    }

    private PreferenceRequest buildPreferenceRequest(Rental rental, BigDecimal amount, BusinessConfig config) {
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
                        .plusSeconds(config.getHoldDurationSeconds())
                        .atOffset(ZoneOffset.UTC))
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(mpProperties.backUrlSuccess())
                        .failure(mpProperties.backUrlFailure())
                        .build())
                .autoReturn(PAYMENT_APPROVED)
                .build();
    }
}
