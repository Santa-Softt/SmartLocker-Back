package com.smartlockr.billing.infrastructure.web.controller;

import com.smartlockr.billing.application.service.MercadoPagoSignatureValidator;
import com.smartlockr.billing.application.service.WebhookProcessingService;
import com.smartlockr.shared.messaging.MessageDispatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MercadoPagoWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class MercadoPagoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookProcessingService webhookProcessingService;

    @MockitoBean
    private MercadoPagoSignatureValidator signatureValidator;

    @Test
    @DisplayName("POST /api/v1/webhooks/mercadopago - queues valid payment webhook")
    void shouldQueueValidPaymentWebhook() throws Exception {
        given(signatureValidator.isValid("signature", "request-1", "12345")).willReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .header("x-signature", "signature")
                        .header("x-request-id", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "payment",
                                  "data": { "id": "12345" }
                                }
                                """))
                .andExpect(status().isOk());

        then(webhookProcessingService).should().processPaymentAsync("12345", "request-1");
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/mercadopago - returns 503 when RabbitMQ is unavailable")
    void shouldReturnServiceUnavailableWhenWebhookCannotBeQueued() throws Exception {
        given(signatureValidator.isValid(anyString(), anyString(), anyString())).willReturn(true);
        doThrow(new MessageDispatchException("RabbitMQ unavailable", new RuntimeException("connection refused")))
                .when(webhookProcessingService)
                .processPaymentAsync("12345", "request-1");

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .header("x-signature", "signature")
                        .header("x-request-id", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "payment",
                                  "data": { "id": "12345" }
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/mercadopago - rejects invalid signature")
    void shouldRejectInvalidSignature() throws Exception {
        given(signatureValidator.isValid("signature", "request-1", "12345")).willReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .header("x-signature", "signature")
                        .header("x-request-id", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "payment",
                                  "data": { "id": "12345" }
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
