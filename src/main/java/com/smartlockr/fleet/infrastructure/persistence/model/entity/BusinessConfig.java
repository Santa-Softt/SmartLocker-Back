package com.smartlockr.fleet.infrastructure.persistence.model.entity;

import com.smartlockr.billing.infrastructure.persistence.model.entity.Rate;
import com.smartlockr.fleet.domain.enums.ServiceStatus;
import com.smartlockr.fleet.infrastructure.dto.UpdateBusinessConfigCommand;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
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
    private UUID id;
    private int holdDurationSeconds;

    private int minRentalDurationMinutes;
    private int maxRentalDurationMinutes;

    private int penaltyPercentage;
    private int streakThreshold;
    private int streakDiscountPercentage;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(32)")
    private ServiceStatus serviceStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_configs_rates", joinColumns = @JoinColumn(name = "business_config_id"))
    private List<Rate> rates;

    /**
     * Applies the values from the given command to this configuration.
     * This is the only sanctioned way to mutate a BusinessConfig instance.
     *
     * @param command the command carrying updated configuration values
     */
    public void applyUpdate(UpdateBusinessConfigCommand command) {
        this.holdDurationSeconds = command.holdDurationSeconds();
        this.minRentalDurationMinutes = command.minRentalDurationMinutes();
        this.maxRentalDurationMinutes = command.maxRentalDurationMinutes();
        this.penaltyPercentage = command.penaltyPercentage();
        this.streakThreshold = command.streakThreshold();
        this.streakDiscountPercentage = command.streakDiscountPercentage();
        this.serviceStatus = command.serviceStatus();
        this.rates = new ArrayList<>(command.rates());
    }

}
