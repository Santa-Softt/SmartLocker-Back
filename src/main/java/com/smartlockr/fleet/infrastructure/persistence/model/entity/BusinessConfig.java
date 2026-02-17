package com.smartlockr.fleet.infrastructure.persistence.model.entity;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Entity
@Builder
@Table(name = "business_configs")
public class BusinessConfig {
    @Id
    @UuidGenerator
    private UUID uuid;
    private int holdDurationSeconds;

    // Definición de límites para la duración del alquiler
    private int minRentalDurationMinutes;
    private int maxRentalDurationMinutes;

    private int penaltyPercentage;
    private int streakThreshold;
    private int streakDiscountPercentage;

    @Enumerated(EnumType.STRING)
    private ServiceStatus serviceStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_configs_rates", joinColumns = @JoinColumn(name = "business_config_id"))
    private List<Rate> rates;

}
