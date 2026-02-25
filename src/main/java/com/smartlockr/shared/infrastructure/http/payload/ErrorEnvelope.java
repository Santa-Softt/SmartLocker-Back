package com.smartlockr.shared.infrastructure.http.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

 
/**
 * Standard API error payload returned by HTTP endpoints.
 * Fields:
 * - path: request path
 * - error: short error identifier
 * - message: human-readable error message
 * - status: HTTP status code
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(String path,
                            String error,
                            String message,
                            int status) {
}
