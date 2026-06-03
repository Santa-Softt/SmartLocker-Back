package com.smartlockr.fleet.infrastructure.persistence.model.entity;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.rental.infrastructure.persistence.entity.model.Rental;
import com.smartlockr.shared.infrastructure.persistence.UuidV7ValueGenerator;
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
        indexes = {
                @Index(name = "idx_locker_allocation", columnList = "size, state")})
public class Locker {
    @UuidGenerator(algorithm = UuidV7ValueGenerator.class)
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String label;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(16)")
    private LockerSize size;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private LockerState state;
    @OneToMany(mappedBy = "locker", fetch = FetchType.LAZY)
    private List<Rental> rentals;

    public void allocate() {
        if (this.state != LockerState.AVAILABLE) {
            throw new IllegalStateException("Invariant violation: Locker " + id + " is not available.");
        }
        this.state = LockerState.HOLD;
    }
}
