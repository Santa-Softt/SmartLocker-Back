package com.smartlockr.iam.infrastructure.security.error.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint,
        AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    // Manejo de 401 Unauthorized (Fallo de autenticación / Sin token)
    // Maneja todas las excepciones de AuthenticationException
    // Si a futuro queremos personalizarla usar instanceof
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "No estás autorizado",
                authException.getMessage(), request.getServletPath());
    }

    // Manejo de 403 Forbidden (Autenticado pero sin permisos/roles)
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException {
        writeResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "No tienes permisos para acceder a este recurso", request.getServletPath());
    }

    private void writeResponse(HttpServletResponse response, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

