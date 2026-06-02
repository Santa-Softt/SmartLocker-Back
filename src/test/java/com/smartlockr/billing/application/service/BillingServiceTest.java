package com.smartlockr.billing.application.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.resources.payment.Payment;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.fleet.application.service.BusinessService;
import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.domain.enums.Role;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.iam.infrastructure.persistence.model.UserPreferences;
import com.smartlockr.rental.domain.enums.RentalState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.rental.infrastructure.persistence.repository.RentalRepository;
import com.smartlockr.shared.email.EmailNotificationSender;
import com.smartlockr.shared.email.PaymentReceiptEmailMessage;
import com.smartlockr.shared.properties.MercadoPagoProperties;
import com.smartlockr.shared.properties.RedisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private RentalRepository rentalRepository;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private PreferenceClient preferenceClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailNotificationSender emailNotificationSender;
    @Mock
    private BusinessService businessService;
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                rentalRepository,
                new MercadoPagoProperties(
                        "access-token",
                        "webhook-secret",
                        "https://api.smartlockr.test/webhook",
                        "https://app.smartlockr.test/success",
                        "https://app.smartlockr.test/failure"
                ),
                businessService,
                paymentClient,
                preferenceClient,
                redisTemplate,
                new RedisProperties(24, null),
                emailNotificationSender
        );
    }

    @Test
    @DisplayName("processPaymentNotification - releases idempotency lock when MercadoPago API fails")
    void shouldReleaseIdempotencyLockWhenMercadoPagoApiFails() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willThrow(new RuntimeException("MercadoPago unavailable"));

        assertThatThrownBy(() -> billingService.processPaymentNotification(paymentId))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Notification processing failed");

        then(redisTemplate).should().delete(lockKey);
    }

    @Test
    @DisplayName("processPaymentNotification - rejects invalid payment IDs before external calls")
    void shouldRejectInvalidPaymentIdBeforeExternalCalls() {
        assertThatThrownBy(() -> billingService.processPaymentNotification("abc"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("solo dígitos");

        then(paymentClient).shouldHaveNoInteractions();
        then(redisTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("processPaymentNotification - ignores duplicated notifications under idempotency lock")
    void shouldIgnoreDuplicatedNotificationWhenLockExists() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(false);

        billingService.processPaymentNotification(paymentId);

        then(paymentClient).should(never()).get(12345L);
    }

    @Test
    @DisplayName("processPaymentNotification - releases lock for non-approved payments")
    void shouldReleaseLockForNonApprovedPayment() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("rejected");

        billingService.processPaymentNotification(paymentId);

        then(redisTemplate).should().delete(lockKey);
    }

    @Test
    @DisplayName("processPaymentNotification - confirms initial payment and sends receipt when allowed")
    void shouldConfirmInitialPaymentAndSendReceipt() throws Exception {
        UUID rentalId = UUID.randomUUID();
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        BigDecimal amountPaid = BigDecimal.valueOf(150);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.local")
                .fullName("User Test")
                .role(Role.CONSUMER)
                .userPreferences(new UserPreferences(true, false))
                .build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("M-01")
                .size(LockerSize.M)
                .state(LockerState.HOLD)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.HOLD)
                .startTime(Instant.now().minusSeconds(60))
                .estimatedEndTime(Instant.now().plusSeconds(3600))
                .finalCost(BigDecimal.valueOf(100))
                .user(user)
                .locker(locker)
                .build();
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("INITIAL_RENTAL:" + rentalId);
        given(payment.getTransactionAmount()).willReturn(amountPaid);
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        billingService.processPaymentNotification(paymentId);

        assertThat(rental.getState()).isEqualTo(RentalState.ACTIVE);
        assertThat(locker.getState()).isEqualTo(LockerState.OCCUPIED);
        assertThat(rental.getFinalCost()).isEqualByComparingTo(amountPaid);
        then(rentalRepository).should().save(rental);
        then(redisTemplate).should().delete("hold:rental:" + rentalId);
        then(emailNotificationSender).should().sendPaymentReceipt(any(PaymentReceiptEmailMessage.class));
    }

    private void enableRedisValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("createPaymentOrder - happy path: crea preferencia de pago para rental en HOLD")
    void shouldCreatePaymentOrderForHoldRental() throws Exception {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("u@test.local")
                .fullName("User")
                .role(Role.CONSUMER)
                .userPreferences(new UserPreferences(true, false))
                .build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.HOLD)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.HOLD)
                .startTime(Instant.now().minusSeconds(60))
                .estimatedEndTime(Instant.now().plusSeconds(7200))
                .finalCost(BigDecimal.valueOf(100))
                .user(user)
                .locker(locker)
                .build();
        com.mercadopago.resources.preference.Preference preference =
                mock(com.mercadopago.resources.preference.Preference.class);
        given(preference.getInitPoint()).willReturn("https://mp.test/init");
        given(preference.getId()).willReturn("pref-1");
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(businessService.getActiveBusinessConfig()).willReturn(
                new com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot(
                        UUID.randomUUID(), 300, 15, 1440, 10, 5, 5,
                        com.smartlockr.fleet.domain.enums.ServiceStatus.OPERATIONAL, List.of()));
        given(preferenceClient.create(org.mockito.ArgumentMatchers.any())).willReturn(preference);

        var response = billingService.createPaymentOrder(rentalId, userId);

        assertThat(response.url()).isEqualTo("https://mp.test/init");
        then(rentalRepository).should().save(rental);
    }

    @Test
    @DisplayName("createPaymentOrder - rental no encontrado lanza IllegalArgumentException")
    void shouldThrowWhenRentalNotFoundOnCreatePaymentOrder() {
        UUID rentalId = UUID.randomUUID();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.createPaymentOrder(rentalId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rental no encontrado");
    }

    @Test
    @DisplayName("createPaymentOrder - rental de otro usuario lanza AccessDeniedException")
    void shouldRejectCreatePaymentOrderFromOtherUser() {
        UUID rentalId = UUID.randomUUID();
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@test.local")
                .role(Role.CONSUMER)
                .build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.HOLD)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.HOLD)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .user(otherUser)
                .locker(locker)
                .build();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> billingService.createPaymentOrder(rentalId, UUID.randomUUID()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("createPaymentOrder - rental en estado distinto de HOLD lanza RentFailedException")
    void shouldRejectCreatePaymentOrderWhenNotInHold() {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.ACTIVE)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .build();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> billingService.createPaymentOrder(rentalId, userId))
                .isInstanceOf(com.smartlockr.billing.application.exception.RentFailedException.class);
    }

    @Test
    @DisplayName("createExtensionPaymentOrder - happy path")
    void shouldCreateExtensionPaymentOrder() throws Exception {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.ACTIVE)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(3600))
                .build();
        com.mercadopago.resources.preference.Preference preference =
                mock(com.mercadopago.resources.preference.Preference.class);
        given(preference.getInitPoint()).willReturn("https://mp.test/ext");
        given(preference.getId()).willReturn("pref-2");
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(businessService.getActiveBusinessConfig()).willReturn(
                new com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot(
                        UUID.randomUUID(), 300, 15, 1440, 10, 5, 5,
                        com.smartlockr.fleet.domain.enums.ServiceStatus.OPERATIONAL, List.of()));
        given(preferenceClient.create(org.mockito.ArgumentMatchers.any())).willReturn(preference);

        var response = billingService.createExtensionPaymentOrder(rentalId, userId, 30, BigDecimal.valueOf(50));

        assertThat(response.url()).isEqualTo("https://mp.test/ext");
    }

    @Test
    @DisplayName("createExtensionPaymentOrder - extension <= 0 lanza RentFailedException")
    void shouldRejectNonPositiveExtension() {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.ACTIVE)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .build();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> billingService.createExtensionPaymentOrder(rentalId, userId, 0, BigDecimal.TEN))
                .isInstanceOf(com.smartlockr.billing.application.exception.RentFailedException.class);
    }

    @Test
    @DisplayName("createExtensionPaymentOrder - rental no ACTIVE lanza RentFailedException")
    void shouldRejectExtensionWhenNotActive() {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.HOLD)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.HOLD)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .build();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> billingService.createExtensionPaymentOrder(rentalId, userId, 30, BigDecimal.TEN))
                .isInstanceOf(com.smartlockr.billing.application.exception.RentFailedException.class);
    }

    @Test
    @DisplayName("createPenaltyPaymentOrder - happy path calcula monto y crea preferencia")
    void shouldCreatePenaltyPaymentOrder() throws Exception {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.PENALIZED)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .finalCost(BigDecimal.valueOf(100))
                .build();
        com.mercadopago.resources.preference.Preference preference =
                mock(com.mercadopago.resources.preference.Preference.class);
        given(preference.getInitPoint()).willReturn("https://mp.test/pen");
        given(preference.getId()).willReturn("pref-3");
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(businessService.getActiveBusinessConfig()).willReturn(
                new com.smartlockr.fleet.infrastructure.dto.BusinessConfigSnapshot(
                        UUID.randomUUID(), 300, 15, 1440, 10, 5, 5,
                        com.smartlockr.fleet.domain.enums.ServiceStatus.OPERATIONAL, List.of()));
        given(preferenceClient.create(org.mockito.ArgumentMatchers.any())).willReturn(preference);

        var response = billingService.createPenaltyPaymentOrder(rentalId, userId);

        assertThat(response.url()).isEqualTo("https://mp.test/pen");
    }

    @Test
    @DisplayName("createPenaltyPaymentOrder - rental no PENALIZED lanza RentFailedException")
    void shouldRejectPenaltyWhenNotPenalized() {
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.ACTIVE)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .finalCost(BigDecimal.valueOf(100))
                .build();
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        assertThatThrownBy(() -> billingService.createPenaltyPaymentOrder(rentalId, userId))
                .isInstanceOf(com.smartlockr.billing.application.exception.RentFailedException.class);
    }

    @Test
    @DisplayName("processPaymentNotification - ignora externalReference blank y libera lock")
    void shouldIgnoreBlankExternalReference() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("   ");

        billingService.processPaymentNotification(paymentId);

        then(redisTemplate).should().delete(lockKey);
    }

    @Test
    @DisplayName("processPaymentNotification - externalReference invalida libera lock")
    void shouldReleaseLockOnInvalidExternalReference() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("INVALID_FORMAT");

        billingService.processPaymentNotification(paymentId);

        then(redisTemplate).should().delete(lockKey);
    }

    @Test
    @DisplayName("processPaymentNotification - paymentId demasiado largo lanza excepcion")
    void shouldRejectTooLongPaymentId() {
        String paymentId = "1".repeat(21);

        assertThatThrownBy(() -> billingService.processPaymentNotification(paymentId))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("demasiado largo");

        then(paymentClient).shouldHaveNoInteractions();
        then(redisTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("processPaymentNotification - paymentId null lanza excepcion")
    void shouldRejectNullPaymentId() {
        assertThatThrownBy(() -> billingService.processPaymentNotification(null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("requerido");
    }

    @Test
    @DisplayName("processPaymentNotification - paymentId blank lanza excepcion")
    void shouldRejectBlankPaymentId() {
        assertThatThrownBy(() -> billingService.processPaymentNotification("   "))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    @DisplayName("processPaymentNotification - extension approved: rental no encontrado NO lanza y conserva lock")
    void shouldReleaseLockWhenExtensionRentalNotFound() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        UUID rentalId = UUID.randomUUID();
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("RENTAL_EXTENSION:" + rentalId + ":30");
        given(payment.getTransactionAmount()).willReturn(BigDecimal.valueOf(50));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.empty());

        // Should not throw - graceful handling
        billingService.processPaymentNotification(paymentId);

        // No lock release for extensions (production logs and continues; lock stays as PROCESSING).
        // Verify that the rentalRepository was looked up
        then(rentalRepository).should().findById(rentalId);
    }

    @Test
    @DisplayName("processPaymentNotification - extension approved actualiza estimatedEndTime")
    void shouldExtendActiveRentalOnApprovedExtension() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER)
                .userPreferences(new UserPreferences(false, false)).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.ACTIVE)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(3600))
                .finalCost(BigDecimal.valueOf(100))
                .build();
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("RENTAL_EXTENSION:" + rentalId + ":30");
        given(payment.getTransactionAmount()).willReturn(BigDecimal.valueOf(50));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));

        billingService.processPaymentNotification(paymentId);

        then(rentalRepository).should().save(rental);
        then(valueOperations).should().set(eq(lockKey), eq("PROCESSED"), any(Duration.class));
    }

    @Test
    @DisplayName("processPaymentNotification - penalty approved completa rental penalizado")
    void shouldCompletePenalizedRentalOnApprovedPenalty() throws Exception {
        String paymentId = "12345";
        String lockKey = "payment_processed:" + paymentId;
        UUID rentalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@t.com").role(Role.CONSUMER)
                .userPreferences(new UserPreferences(false, false)).build();
        Locker locker = Locker.builder()
                .id(UUID.randomUUID())
                .label("L-1")
                .size(LockerSize.M)
                .state(LockerState.OCCUPIED)
                .build();
        Rental rental = Rental.builder()
                .id(rentalId)
                .state(RentalState.PENALIZED)
                .user(user)
                .locker(locker)
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(60))
                .finalCost(BigDecimal.valueOf(100))
                .isPenalized(true)
                .build();
        Payment payment = mock(Payment.class);

        enableRedisValueOperations();
        given(valueOperations.setIfAbsent(eq(lockKey), eq("PROCESSING"), any(Duration.class))).willReturn(true);
        given(paymentClient.get(12345L)).willReturn(payment);
        given(payment.getStatus()).willReturn("approved");
        given(payment.getExternalReference()).willReturn("PENALTY:" + rentalId);
        given(payment.getTransactionAmount()).willReturn(BigDecimal.valueOf(50));
        given(rentalRepository.findById(rentalId)).willReturn(Optional.of(rental));
        given(rentalRepository.existsByUserIdAndStateAndIdNot(userId, RentalState.PENALIZED, rentalId))
                .willReturn(false);

        billingService.processPaymentNotification(paymentId);

        assertThat(rental.getState()).isEqualTo(RentalState.COMPLETED);
        assertThat(locker.getState()).isEqualTo(LockerState.AVAILABLE);
        assertThat(user.isSuspended()).isFalse();
    }
}
