package com.smartlockr.iam.infrastructure.rest.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartlockr.rental.infrastructure.dto.ActiveRentalSnapshot;

public record SessionResponse(
        @JsonProperty("user")
        UserResponse userResponse,
        @JsonProperty("session")
        TokenDetails tokenDetails,
        @JsonProperty("activeRental")
        ActiveRentalSnapshot activeRental
) {
    public SessionResponse(UserResponse userResponse, TokenDetails tokenDetails) {
        this(userResponse, tokenDetails, null);
    }
}
