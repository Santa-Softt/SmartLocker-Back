package com.smartlockr.rental.infrastructure.dto;

import com.smartlockr.fleet.domain.enums.LockerSize;
import com.smartlockr.fleet.domain.enums.LockerState;
import com.smartlockr.rental.domain.enums.RentalState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRentalSnapshotTest {

    @Test
    @DisplayName("constructor y accessors devuelven los mismos valores")
    void shouldExposeAllFields() {
        // GIVEN
        UUID rentalId = UUID.randomUUID();
        UUID lockerId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Instant end = Instant.parse("2026-01-01T12:00:00Z");
        BigDecimal cost = new BigDecimal("99.99");

        // WHEN
        ActiveRentalSnapshot snapshot = new ActiveRentalSnapshot(
                rentalId, RentalState.ACTIVE,
                lockerId, "L-01", LockerSize.M, LockerState.OCCUPIED,
                start, end, cost, true);

        // THEN
        assertThat(snapshot.rentalId()).isEqualTo(rentalId);
        assertThat(snapshot.state()).isEqualTo(RentalState.ACTIVE);
        assertThat(snapshot.lockerId()).isEqualTo(lockerId);
        assertThat(snapshot.lockerLabel()).isEqualTo("L-01");
        assertThat(snapshot.lockerSize()).isEqualTo(LockerSize.M);
        assertThat(snapshot.lockerState()).isEqualTo(LockerState.OCCUPIED);
        assertThat(snapshot.startTime()).isEqualTo(start);
        assertThat(snapshot.estimatedEndTime()).isEqualTo(end);
        assertThat(snapshot.finalCost()).isEqualByComparingTo(cost);
        assertThat(snapshot.penalized()).isTrue();
    }

    @Test
    @DisplayName("permite campos nulos para lockers y cost")
    void shouldAllowNullLockerFields() {
        ActiveRentalSnapshot snapshot = new ActiveRentalSnapshot(
                UUID.randomUUID(), RentalState.HOLD,
                null, null, null, null,
                Instant.now(), Instant.now().plusSeconds(60),
                null, false);

        assertThat(snapshot.lockerId()).isNull();
        assertThat(snapshot.lockerLabel()).isNull();
        assertThat(snapshot.lockerSize()).isNull();
        assertThat(snapshot.lockerState()).isNull();
        assertThat(snapshot.finalCost()).isNull();
        assertThat(snapshot.penalized()).isFalse();
    }

    @Test
    @DisplayName("equals/hashCode se basan en los componentes del record")
    void shouldRespectRecordEquality() {
        UUID rentalId = UUID.randomUUID();
        Instant now = Instant.now();
        ActiveRentalSnapshot a = new ActiveRentalSnapshot(
                rentalId, RentalState.ACTIVE, null, null, null, null, now, now, BigDecimal.ONE, false);
        ActiveRentalSnapshot b = new ActiveRentalSnapshot(
                rentalId, RentalState.ACTIVE, null, null, null, null, now, now, BigDecimal.ONE, false);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
