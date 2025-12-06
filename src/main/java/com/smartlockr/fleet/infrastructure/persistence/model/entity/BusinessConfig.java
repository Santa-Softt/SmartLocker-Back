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
    private int streakThreshold;
    private int streakDiscountPercentage;
    private ServiceStatus serviceStatus;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_configs_rates", joinColumns = @JoinColumn(name = "business_config_id"))
    private List<Rate> rates;

}
