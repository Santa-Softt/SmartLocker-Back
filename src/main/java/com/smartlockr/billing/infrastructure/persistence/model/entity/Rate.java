package com.smartlockr.billing.infrastructure.persistence.model.entity;

import com.smartlockr.fleet.domain.enums.LockerSize;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Embeddable
public class Rate {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockerSize size;

    @Column(nullable = false)
    private BigDecimal hourlyRate;
}
