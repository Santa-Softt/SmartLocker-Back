package com.smartlockr.iam.infrastructure.rest.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionResponse(
        @JsonProperty("user")
        UserResponse userResponse,
        @JsonProperty("jwt")
        TokenDetails tokenDetails
) {
}
