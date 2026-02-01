package com.smartlockr.shared.infrastructure.http.handler;

import com.smartlockr.shared.infrastructure.http.payload.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
