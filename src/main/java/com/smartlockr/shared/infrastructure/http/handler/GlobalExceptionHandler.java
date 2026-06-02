package com.smartlockr.shared.infrastructure.http.handler;

import com.mercadopago.exceptions.MPException;
import com.smartlockr.billing.application.exception.InvalidMPSignatureException;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.shared.i18n.MessageResolver;
import com.smartlockr.shared.infrastructure.http.payload.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectProvider<MessageResolver> messageResolverProvider;

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<ErrorEnvelope> refusedConnection(HttpServletRequest request, CannotCreateTransactionException ex) {
        var responseBody = new ErrorEnvelope(request.getRequestURI(),
                "DATABASE_UNAVAILABLE",
                resolve("error.database.unavailable"),
                503
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(responseBody);
    }

    @ExceptionHandler(InvalidMPSignatureException.class)
    public ResponseEntity<String> handleInvalidSignature(InvalidMPSignatureException ex) {
        log.error("Firma inválida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resolve("error.payment.invalid-signature"));
    }

    @ExceptionHandler(MPException.class)
    public ResponseEntity<String> handleMPException(MPException ex) {
        log.error("Error al comunicarse con MercadoPago", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(resolve("error.payment.notification"));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<String> handlePaymentGateway(PaymentGatewayException ex) {
        log.error("Error en pasarela de pago", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resolve("error.payment.gateway"));
    }

    private String resolve(String code) {
        MessageResolver messageResolver = messageResolverProvider.getIfAvailable();
        return messageResolver == null ? code : messageResolver.resolve(code, LocaleContextHolder.getLocale());
    }
}
