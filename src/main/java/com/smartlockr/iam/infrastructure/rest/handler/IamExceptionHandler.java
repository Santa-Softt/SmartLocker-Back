package com.smartlockr.iam.infrastructure.rest.handler;

import com.smartlockr.shared.infrastructure.http.payload.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class IamExceptionHandler {

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorEnvelope> cookieIsNotPresent(HttpServletRequest request){
        var envelope = new ErrorEnvelope(
                request.getRequestURI(),
                "No estás autorizado",
                "No se encontro una cookie con el nombre 'refresh_token'",
                400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(envelope);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorEnvelope> invalidRefreshToken(NoSuchElementException ex, HttpServletRequest request){
        var envelope = new ErrorEnvelope(
                request.getRequestURI(),
                "No estás autorizado",
                ex.getMessage(),
                401);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(envelope);
    }

}
