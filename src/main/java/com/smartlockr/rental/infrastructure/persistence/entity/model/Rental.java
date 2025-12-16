package com.smartlockr.rental.infrastructure.persistence.entity.model;

import com.smartlockr.fleet.infrastructure.persistence.model.entity.Locker;
import com.smartlockr.iam.infrastructure.persistence.model.User;
import com.smartlockr.rental.domain.enums.RentalState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Entity
@Builder
@Table(name = "rentals",
        indexes = {
                @Index(name = "idx_rental_user_id", columnList = "user_id"),
                @Index(name = "idx_rental_locker_id", columnList = "locker_id"),
                @Index(name = "idx_rental_state", columnList = "state")
        })
public class Rental {
    @UuidGenerator
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private RentalState state;
    private Instant startTime;
    private Instant estimatedEndTime;
    private Instant endTime;
    private BigDecimal finalCost;
    private boolean isPenalized;
    @ManyToOne
    private User user;
    @ManyToOne
    private Locker locker;
}
