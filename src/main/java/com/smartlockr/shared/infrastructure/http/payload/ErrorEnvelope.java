package com.smartlockr.shared.infrastructure.http.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(String path,
                            String error,
                            String message,
                            int status) {
}
