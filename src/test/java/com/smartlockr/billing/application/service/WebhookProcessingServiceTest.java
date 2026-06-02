package com.smartlockr.billing.application.service;

import com.smartlockr.billing.application.messaging.PaymentWebhookMessagePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    @Mock
    private PaymentWebhookMessagePublisher publisher;

    @Test
    @DisplayName("processPaymentAsync(paymentId) - delega al publisher con requestId null")
    void shouldDelegateToPublisherWithNullRequestId() {
        WebhookProcessingService service = new WebhookProcessingService(publisher);

        service.processPaymentAsync("12345");

        then(publisher).should().publishPaymentWebhook("12345", null);
    }

    @Test
    @DisplayName("processPaymentAsync(paymentId, requestId) - delega al publisher con ambos argumentos")
    void shouldDelegateToPublisherWithRequestId() {
        WebhookProcessingService service = new WebhookProcessingService(publisher);

        service.processPaymentAsync("12345", "req-abc");

        then(publisher).should().publishPaymentWebhook("12345", "req-abc");
    }

    @Test
    @DisplayName("processPaymentAsync(paymentId) - usa la sobrecarga que rellena requestId con null")
    void shouldUseOverloadThatFillsRequestIdWithNull() {
        WebhookProcessingService service = new WebhookProcessingService(publisher);

        service.processPaymentAsync("99999");

        then(publisher).should(times(1)).publishPaymentWebhook("99999", null);
    }
}
