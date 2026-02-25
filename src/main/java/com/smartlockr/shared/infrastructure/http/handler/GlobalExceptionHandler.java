package com.smartlockr.shared.infrastructure.http.handler;

import com.mercadopago.exceptions.MPException;
import com.smartlockr.billing.application.exception.InvalidMPSignatureException;
import com.smartlockr.billing.application.exception.PaymentGatewayException;
import com.smartlockr.shared.infrastructure.http.payload.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<ErrorEnvelope> refusedConnection(HttpServletRequest request, CannotCreateTransactionException ex) {
        var responseBody = new ErrorEnvelope(request.getRequestURI(),
                ex.getMessage(),
                "Database is unavailable",
                503
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(responseBody);
    }

    @ExceptionHandler(InvalidMPSignatureException.class)
    public ResponseEntity<String> handleInvalidSignature(InvalidMPSignatureException ex) {
        log.error("Firma inválida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
    }

    @ExceptionHandler(MPException.class)
    public ResponseEntity<String> handleMPException(MPException ex) {
        log.error("Error al comunicarse con MercadoPago", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing payment notification");
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<String> handlePaymentGateway(PaymentGatewayException ex) {
        log.error("Error en pasarela de pago", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
