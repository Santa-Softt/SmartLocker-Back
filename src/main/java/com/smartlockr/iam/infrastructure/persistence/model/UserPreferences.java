package com.smartlockr.iam.infrastructure.persistence.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Builder.Default
    private boolean receiveReceipts = true;

    @Builder.Default
    private boolean receivesPromotions = false;
}
