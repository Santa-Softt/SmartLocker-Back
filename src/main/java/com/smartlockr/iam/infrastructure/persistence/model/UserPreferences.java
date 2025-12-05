package com.smartlockr.iam.infrastructure.persistence.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPreferences implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private boolean receiveReceipts = true;

    @Builder.Default
    private boolean receivesPromotions = false;
}
