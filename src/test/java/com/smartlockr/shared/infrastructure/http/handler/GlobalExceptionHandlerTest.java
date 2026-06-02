package com.smartlockr.shared.infrastructure.http.handler;

import com.mercadopago.exceptions.MPException;
import com.smartlockr.billing.application.exception.InvalidMPSignatureException;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.shared.i18n.MessageResolver;
import com.smartlockr.shared.infrastructure.http.payload.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ObjectProvider<MessageResolver> messageResolverProvider;

    @Mock
    private MessageResolver messageResolver;

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageResolverProvider);
        when(messageResolverProvider.getIfAvailable()).thenReturn(messageResolver);
        lenient().when(messageResolver.resolve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Locale.class)))
                .thenAnswer(inv -> "msg:" + inv.getArgument(0));
    }

    @Test
    @DisplayName("refusedConnection - devuelve 503 SERVICE_UNAVAILABLE con envelope de error")
    void shouldReturnServiceUnavailableForDbConnectionError() {
        when(request.getRequestURI()).thenReturn("/api/test");
        var ex = new CannotCreateTransactionException("DB connection refused", new RuntimeException());

        ResponseEntity<ErrorEnvelope> response = handler.refusedConnection(request, ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ErrorEnvelope body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(503);
        assertThat(body.error()).isEqualTo("DATABASE_UNAVAILABLE");
        assertThat(body.path()).isEqualTo("/api/test");
        assertThat(body.message()).isEqualTo("msg:error.database.unavailable");
    }

    @Test
    @DisplayName("handleInvalidSignature - devuelve 401 UNAUTHORIZED")
    void shouldReturnUnauthorizedForInvalidSignature() {
        var ex = new InvalidMPSignatureException("firma no valida");

        ResponseEntity<String> response = handler.handleInvalidSignature(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo("msg:error.payment.invalid-signature");
    }

    @Test
    @DisplayName("handleMPException - devuelve 500 INTERNAL_SERVER_ERROR")
    void shouldReturnInternalServerErrorForMPException() {
        var ex = new MPException("MP API error");

        ResponseEntity<String> response = handler.handleMPException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("msg:error.payment.notification");
    }

    @Test
    @DisplayName("handlePaymentGateway - devuelve 400 BAD_REQUEST")
    void shouldReturnBadRequestForPaymentGateway() {
        var ex = new PaymentGatewayException("gateway error");

        ResponseEntity<String> response = handler.handlePaymentGateway(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("msg:error.payment.gateway");
    }

    @Test
    @DisplayName("resolve - si MessageResolver no esta disponible, devuelve el codigo")
    void shouldReturnCodeWhenResolverNotAvailable() {
        when(messageResolverProvider.getIfAvailable()).thenReturn(null);

        ResponseEntity<String> response = handler.handleInvalidSignature(new InvalidMPSignatureException("x"));

        assertThat(response.getBody()).isEqualTo("error.payment.invalid-signature");
    }
}
