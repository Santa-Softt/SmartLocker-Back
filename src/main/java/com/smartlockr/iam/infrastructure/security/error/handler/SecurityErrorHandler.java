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

/**
 * Centralized security exception handler for Spring Security.
 * Responsibilities:
 * - Handles authentication failures by returning a 401 Unauthorized JSON response.
 * - Handles access denials by returning a 403 Forbidden JSON response.
 * This class is used as both an {@link AuthenticationEntryPoint} and an {@link AccessDeniedHandler}.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint,
        AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    /**
     * Called by Spring Security when a request is not authenticated.
     * Produces a 401 Unauthorized JSON response.
     * @param request current HTTP request
     * @param response current HTTP response
     * @param authException authentication error
     * @throws IOException when the response body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "No estás autorizado",
                authException.getMessage(), request.getServletPath());
    }

    /**
     * Called by Spring Security when an authenticated user does not have sufficient permissions.
     * Produces a 403 Forbidden JSON response.
     * @param request current HTTP request
     * @param response current HTTP response
     * @param accessDeniedException access control error
     * @throws IOException when the response body cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException {
        writeResponse(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "No tienes permisos para acceder a este recurso", request.getServletPath());
    }

    /**
     * Writes a JSON error response using a consistent structure.
     * Body fields:
     * - status: HTTP status code
     * - error: short error label
     * - message: human-readable message
     * - path: request path
     * @param response HTTP response to write
     * @param status HTTP status code
     * @param error short error label
     * @param message human-readable message
     * @param path request path
     * @throws IOException when writing to the output stream fails
     */
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
