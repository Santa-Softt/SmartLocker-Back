package com.smartlockr.fleet.infrastructure.persistence.model.entity;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
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
@Table(name = "lockers",
        indexes = { @Index(name = "idx_locker_label", columnList = "label", unique = true) })
public class Locker {
    @UuidGenerator
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String label;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockerSize size;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LockerState state;
    @OneToMany(mappedBy = "locker", fetch = FetchType.LAZY)
    private List<Rental> rentals;
}
