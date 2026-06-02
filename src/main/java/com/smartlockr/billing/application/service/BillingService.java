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
import com.smartlockr.shared.email.EmailNotificationSender;
import com.smartlockr.shared.email.PaymentReceiptEmailMessage;
import com.smartlockr.shared.properties.MercadoPagoProperties;
import com.smartlockr.shared.properties.RedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final RedisProperties redisProperties;
    private final EmailNotificationSender emailNotificationSender;
    private static final String CURRENCY_ARS = "ARS";
    private static final String PAYMENT_APPROVED = "approved";
    private static final String HOLD_KEY_PREFIX = "hold:rental:";
    private static final String ACTIVE_KEY_PREFIX = "active:rental:";
    private static final String INITIAL_RENTAL_PAYMENT = "INITIAL_RENTAL";
    private static final String RENTAL_EXTENSION_PAYMENT = "RENTAL_EXTENSION";
    private static final String PENALTY_PAYMENT = "PENALTY";
    private static final String PAYMENT_REFERENCE_SEPARATOR = ":";

    /**
     * Creates a payment order for an existing rental in HOLD state.
     * Validates that the rental belongs to the authenticated user.
     *
     * @param rentalId the UUID of the rental to create a payment order for
     * @param userId the UUID of the authenticated user requesting the payment order
     * @return a PaymentLinkResponse containing the payment link URL
     * @throws IllegalArgumentException if the rental is not found
     * @throws RentFailedException if the rental is not in HOLD state
     * @throws AccessDeniedException if the user does not own the rental
     * @throws PaymentGatewayException if communication with payment gateway fails
     */
    @Transactional
    public PaymentLinkResponse createPaymentOrder(UUID rentalId, UUID userId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental no encontrado: " + rentalId));

        if (!rental.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User does not own this rental");
        }

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
            PreferenceRequest preferenceRequest = buildPreferenceRequest(
                    rental,
                    establishedPrice,
                    config,
                    "Alquiler Locker - Tamaño " + rental.getLocker().getSize(),
                    buildInitialRentalReference(rental.getId())
            );
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
    public PaymentLinkResponse createExtensionPaymentOrder(UUID rentalId,
                                                           UUID userId,
                                                           int extensionMinutes,
                                                           BigDecimal extensionPrice) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental no encontrado: " + rentalId));

        if (!rental.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User does not own this rental");
        }

        if (rental.getState() != RentalState.ACTIVE) {
            throw new RentFailedException("Solo se pueden extender alquileres activos.");
        }

        if (extensionMinutes <= 0) {
            throw new RentFailedException("La extensión debe ser mayor a 0 minutos.");
        }

        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();

        try {
            PreferenceRequest preferenceRequest = buildPreferenceRequest(
                    rental,
                    extensionPrice,
                    config,
                    "Extensión Locker - Tamaño " + rental.getLocker().getSize(),
                    buildRentalExtensionReference(rentalId, extensionMinutes)
            );
            Preference preference = preferenceClient.create(preferenceRequest);

            log.info("Preferencia de extensión creada para Rental: {}. ID: {}", rentalId, preference.getId());
            return new PaymentLinkResponse(preference.getInitPoint());

        } catch (MPApiException e) {
            log.error("Error API MercadoPago [Status: {}]: {}", e.getApiResponse().getStatusCode(), e.getApiResponse().getContent());
            throw new PaymentGatewayException("Error en comunicación con pasarela de pago");
        } catch (Exception e) {
            log.error("Error crítico al generar orden de extensión para Rental: {}", rentalId, e);
            throw new PaymentGatewayException("No se pudo procesar la solicitud de extensión");
        }
    }

    @Transactional
    public PaymentLinkResponse createPenaltyPaymentOrder(UUID rentalId, UUID userId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental no encontrado: " + rentalId));

        if (!rental.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("User does not own this rental");
        }

        if (rental.getState() != RentalState.PENALIZED) {
            throw new RentFailedException("Solo se pueden pagar penalizaciones de alquileres penalizados.");
        }

        BigDecimal penaltyAmount = calculatePenaltyAmount(rental);
        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();

        try {
            PreferenceRequest preferenceRequest = buildPreferenceRequest(
                    rental,
                    penaltyAmount,
                    config,
                    "Penalización Locker - Tamaño " + rental.getLocker().getSize(),
                    buildPenaltyReference(rentalId)
            );
            Preference preference = preferenceClient.create(preferenceRequest);

            log.info("Preferencia de penalización creada para Rental: {}. ID: {}", rentalId, preference.getId());
            return new PaymentLinkResponse(preference.getInitPoint());

        } catch (MPApiException e) {
            log.error("Error API MercadoPago [Status: {}]: {}", e.getApiResponse().getStatusCode(), e.getApiResponse().getContent());
            throw new PaymentGatewayException("Error en comunicación con pasarela de pago");
        } catch (Exception e) {
            log.error("Error crítico al generar orden de penalización para Rental: {}", rentalId, e);
            throw new PaymentGatewayException("No se pudo procesar la solicitud de penalización");
        }
    }

    @Transactional
    public void processPaymentNotification(String paymentId) {
        validatePaymentId(paymentId);

        String lockKey = "payment_processed:" + paymentId;
        Boolean isFirstTime = tryAcquireIdempotencyLock(lockKey);

        if (Boolean.FALSE.equals(isFirstTime)) {
            log.info("[MERCADOPAGO] Notificación {} ya en curso o finalizada.", paymentId);
            return;
        }

        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            if (PAYMENT_APPROVED.equalsIgnoreCase(payment.getStatus())) {
                processApprovedPayment(lockKey, paymentId, payment);
            } else {
                safeDeleteLockKey(lockKey);
                log.info("[MERCADOPAGO] Pago {} con estado: {}. Bloqueo liberado.", paymentId, payment.getStatus());
            }

        } catch (NumberFormatException e) {
            safeDeleteLockKey(lockKey);
            log.error("[MERCADOPAGO] ID de pago inválido {}: {}", paymentId, e.getMessage());
            throw new PaymentGatewayException("ID de pago inválido", e);
        } catch (Exception e) {
            safeDeleteLockKey(lockKey);
            log.error("Error crítico procesando notificación {}: {}", paymentId, e.getMessage());
            throw new PaymentGatewayException("Notification processing failed", e);
        }
    }

    /**
     * Attempts to acquire the idempotency lock in Redis.
     * If Redis is unavailable, logs a warning and returns true to allow processing to continue.
     * Duplicate payments are prevented by the rental state check in handleApprovedPayment().
     *
     * @param lockKey the Redis key for idempotency control
     * @return true if this is the first processing attempt, false if already being processed
     */
    private Boolean tryAcquireIdempotencyLock(String lockKey) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "PROCESSING", Duration.ofHours(redisProperties.idempotencyLockTtlHours()));
            if (result == null) {
                log.warn("[REDIS] Idempotency lock returned null for {}. Processing with caution.", lockKey);
                return true;
            }
            return result;
        } catch (Exception e) {
            log.warn("[REDIS] Failed to acquire idempotency lock for {}. " +
                     "Continuing without lock - duplicate prevention will rely on rental state check.", lockKey, e);
            return true;
        }
    }

    /**
     * Safely deletes the idempotency lock key, ignoring Redis failures.
     * Used for cleanup when payment processing fails or is rejected.
     *
     * @param lockKey the Redis key to delete
     */
    private void safeDeleteLockKey(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("[REDIS] Failed to delete lock key {}. Key may expire naturally after 24h.", lockKey, e);
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
            safeDeleteLockKey(lockKey);
            return;
        }

        PaymentReference reference = parsePaymentReference(lockKey, paymentId, externalReference);
        if (reference == null) {
            return;
        }

        handleApprovedPayment(reference, payment);
        safeUpdateLockKeyToProcessed(lockKey);
    }

    /**
     * Safely updates the lock key status to PROCESSED, ignoring Redis failures.
     *
     * @param lockKey the Redis key to update
     */
    private void safeUpdateLockKeyToProcessed(String lockKey) {
        try {
            redisTemplate.opsForValue().set(lockKey, "PROCESSED", Duration.ofHours(redisProperties.idempotencyLockTtlHours()));
        } catch (Exception e) {
            log.warn("[REDIS] Failed to update lock key {} to PROCESSED. Key will expire naturally.", lockKey, e);
        }
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
    private PaymentReference parsePaymentReference(String lockKey, String paymentId, String externalReference) {
        if (!externalReference.contains(PAYMENT_REFERENCE_SEPARATOR)) {
            return parseLegacyInitialPaymentReference(lockKey, paymentId, externalReference);
        }

        String[] parts = externalReference.split(PAYMENT_REFERENCE_SEPARATOR);
        if (parts.length < 2) {
            log.error("[MERCADOPAGO] External reference inválida {}: {}", paymentId, externalReference);
            safeDeleteLockKey(lockKey);
            return null;
        }

        try {
            return switch (parts[0]) {
                case INITIAL_RENTAL_PAYMENT -> new PaymentReference(
                        PaymentPurpose.INITIAL_RENTAL,
                        UUID.fromString(parts[1]),
                        null
                );
                case RENTAL_EXTENSION_PAYMENT -> {
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Invalid rental extension reference");
                    }
                    yield new PaymentReference(
                            PaymentPurpose.RENTAL_EXTENSION,
                            UUID.fromString(parts[1]),
                            Integer.parseInt(parts[2])
                    );
                }
                case PENALTY_PAYMENT -> new PaymentReference(
                        PaymentPurpose.PENALTY,
                        UUID.fromString(parts[1]),
                        null
                );
                default -> throw new IllegalArgumentException("Unknown payment purpose: " + parts[0]);
            };
        } catch (IllegalArgumentException e) {
            log.error("[MERCADOPAGO] External reference inválida {}: {}", paymentId, externalReference);
            safeDeleteLockKey(lockKey);
            return null;
        }
    }

    private PaymentReference parseLegacyInitialPaymentReference(String lockKey, String paymentId, String externalReference) {
        try {
            return new PaymentReference(PaymentPurpose.INITIAL_RENTAL, UUID.fromString(externalReference), null);
        } catch (IllegalArgumentException _) {
            log.error("[MERCADOPAGO] External reference inválida {}: {}", paymentId, externalReference);
            safeDeleteLockKey(lockKey);
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

    private void handleApprovedPayment(PaymentReference reference, Payment payment) {
        if (reference.purpose() == PaymentPurpose.RENTAL_EXTENSION) {
            confirmExtensionPayment(reference.rentalId(), reference.extensionMinutes(), payment.getTransactionAmount());
            return;
        }
        if (reference.purpose() == PaymentPurpose.PENALTY) {
            confirmPenaltyPayment(reference.rentalId(), payment.getTransactionAmount());
            return;
        }

        handleInitialRentalPayment(reference.rentalId(), payment);
    }

    private void handleInitialRentalPayment(UUID rentalId, Payment payment) {
        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {

            if (rental.getState() == RentalState.ACTIVE) {
                log.warn("[PAGO EXCEDENTE DETECTADO]");
                log.warn("El Rental {} ya se encuentra ACTIVE.", rentalId);
                log.warn("Payment ID: {} | Monto: {} {}", payment.getId(), payment.getTransactionAmount(), payment.getCurrencyId());
                log.warn("Acción requerida: CONCILIACIÓN MANUAL.");
                return;
            }

            confirmRentalPayment(rentalId, payment.getTransactionAmount());

        }, () -> log.error("[ERROR] Pago aprobado para Rental inexistente: {}", rentalId));
    }

    private void confirmRentalPayment(UUID rentalId, BigDecimal amountPaid) {
        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {
            if (rental.getState() != RentalState.HOLD) {
                log.warn("[MERCADOPAGO] Pago inicial aprobado para Rental {} en estado {}. Requiere conciliación.",
                        rentalId, rental.getState());
                return;
            }

            rental.setState(RentalState.ACTIVE);
            rental.setFinalCost(amountPaid);

            Locker locker = rental.getLocker();
            locker.setState(LockerState.OCCUPIED);

            rentalRepository.save(rental);
            sendPaymentReceiptIfAllowed(rental, amountPaid);

            safeDeleteLockKey(HOLD_KEY_PREFIX + rentalId);

            setActiveRentalTimer(rental);

            log.info("Pago confirmado exitosamente para Rental: {}. Clave de expiración en Redis eliminada y timer activo iniciado.", rentalId);

        }, () -> log.error("Rental no encontrado tras notificación de pago: {}", rentalId));
    }

    private void confirmExtensionPayment(UUID rentalId, Integer extensionMinutes, BigDecimal amountPaid) {
        if (extensionMinutes == null || extensionMinutes <= 0) {
            log.error("[MERCADOPAGO] Extensión aprobada con duración inválida para Rental {}", rentalId);
            return;
        }

        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {
            if (rental.getState() != RentalState.ACTIVE) {
                log.warn("[MERCADOPAGO] Extensión aprobada para Rental {} en estado {}. Requiere conciliación.",
                        rentalId, rental.getState());
                return;
            }

            Instant extensionBase = rental.getEstimatedEndTime().isAfter(Instant.now())
                    ? rental.getEstimatedEndTime()
                    : Instant.now();

            rental.setEstimatedEndTime(extensionBase.plus(extensionMinutes, ChronoUnit.MINUTES));
            rental.setFinalCost(addMoney(rental.getFinalCost(), amountPaid));
            rentalRepository.save(rental);
            setActiveRentalTimer(rental);
            sendPaymentReceiptIfAllowed(rental, amountPaid);

            log.info("Extensión confirmada para Rental: {} por {} minutos.", rentalId, extensionMinutes);
        }, () -> log.error("[ERROR] Extensión aprobada para Rental inexistente: {}", rentalId));
    }

    private void confirmPenaltyPayment(UUID rentalId, BigDecimal amountPaid) {
        rentalRepository.findById(rentalId).ifPresentOrElse(rental -> {
            if (rental.getState() != RentalState.PENALIZED) {
                log.warn("[MERCADOPAGO] Penalización aprobada para Rental {} en estado {}. Requiere conciliación.",
                        rentalId, rental.getState());
                return;
            }

            rental.setState(RentalState.COMPLETED);
            rental.setPenalized(false);
            rental.setFinalCost(addMoney(rental.getFinalCost(), amountPaid));

            Locker locker = rental.getLocker();
            locker.setState(LockerState.AVAILABLE);

            var user = rental.getUser();
            if (user != null && !rentalRepository.existsByUserIdAndStateAndIdNot(
                    user.getId(), RentalState.PENALIZED, rentalId)) {
                user.setSuspended(false);
                user.setSuspensionTime(null);
            }

            rentalRepository.save(rental);
            safeDeleteLockKey(ACTIVE_KEY_PREFIX + rentalId);
            sendPaymentReceiptIfAllowed(rental, amountPaid);

            log.info("Penalización confirmada y alquiler completado para Rental: {}", rentalId);
        }, () -> log.error("[ERROR] Penalización aprobada para Rental inexistente: {}", rentalId));
    }

    private BigDecimal calculatePenaltyAmount(Rental rental) {
        if (rental.getFinalCost() == null) {
            throw new RentFailedException("El alquiler no tiene costo base para calcular penalización.");
        }

        BusinessConfigSnapshot config = businessService.getActiveBusinessConfig();
        return rental.getFinalCost()
                .multiply(BigDecimal.valueOf(config.penaltyPercentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.CEILING);
    }

    private void setActiveRentalTimer(Rental rental) {
        long rentalExpirationTTL = Duration.between(Instant.now(), rental.getEstimatedEndTime()).getSeconds();
        if (rentalExpirationTTL <= 0) rentalExpirationTTL = 1;

        try {
            redisTemplate.opsForValue().set(ACTIVE_KEY_PREFIX + rental.getId(), "1", rentalExpirationTTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[REDIS] Failed to set active rental timer for {}. Expiration will be managed by reconciliation.",
                    rental.getId(), e);
        }
    }

    private void sendPaymentReceiptIfAllowed(Rental rental, BigDecimal amountPaid) {
        var user = rental.getUser();
        if (user == null || user.getUserPreferences() == null || !user.getUserPreferences().isReceiveReceipts()) {
            return;
        }

        Locker locker = rental.getLocker();
        emailNotificationSender.sendPaymentReceipt(new PaymentReceiptEmailMessage(
                user.getEmail(),
                user.getFullName(),
                rental.getId(),
                locker == null ? null : locker.getLabel(),
                locker == null || locker.getSize() == null ? null : locker.getSize().name(),
                amountPaid,
                rental.getStartTime(),
                rental.getEstimatedEndTime()
        ));
    }

    private BigDecimal addMoney(BigDecimal currentAmount, BigDecimal amountToAdd) {
        BigDecimal safeCurrent = currentAmount == null ? BigDecimal.ZERO : currentAmount;
        BigDecimal safeAmountToAdd = amountToAdd == null ? BigDecimal.ZERO : amountToAdd;
        return safeCurrent.add(safeAmountToAdd);
    }

    private void validateRentalStatus(Rental rental) {
        if (rental.getState() != RentalState.HOLD) {
            throw new RentFailedException("No se puede realizar el alquiler con lockers en estado: " + rental.getState());
        }
    }

    private PreferenceRequest buildPreferenceRequest(Rental rental,
                                                     BigDecimal amount,
                                                     BusinessConfigSnapshot config,
                                                     String title,
                                                     String externalReference) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(title)
                .quantity(1)
                .unitPrice(amount)
                .currencyId(CURRENCY_ARS)
                .build();

        return PreferenceRequest.builder()
                .items(Collections.singletonList(item))
                .externalReference(externalReference)
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

    private String buildInitialRentalReference(UUID rentalId) {
        return INITIAL_RENTAL_PAYMENT + PAYMENT_REFERENCE_SEPARATOR + rentalId;
    }

    private String buildRentalExtensionReference(UUID rentalId, int extensionMinutes) {
        return RENTAL_EXTENSION_PAYMENT + PAYMENT_REFERENCE_SEPARATOR + rentalId +
                PAYMENT_REFERENCE_SEPARATOR + extensionMinutes;
    }

    private String buildPenaltyReference(UUID rentalId) {
        return PENALTY_PAYMENT + PAYMENT_REFERENCE_SEPARATOR + rentalId;
    }

    private enum PaymentPurpose {
        INITIAL_RENTAL,
        RENTAL_EXTENSION,
        PENALTY
    }

    private record PaymentReference(
            PaymentPurpose purpose,
            UUID rentalId,
            Integer extensionMinutes
    ) {
    }
}
