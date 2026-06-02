package com.smartlockr.rental.infrastructure.scheduling;

import com.smartlockr.rental.application.service.RentalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class HoldExpirationTaskTest {

    @Test
    @DisplayName("executeReconciliation - delegates expired holds and penalties processing")
    void shouldDelegateReconciliationWork() {
        RentalService rentalService = mock(RentalService.class);
        given(rentalService.processExpiredHolds()).willReturn(1);
        given(rentalService.processExpiredRentalsToPenalty()).willReturn(2);
        HoldExpirationTask task = new HoldExpirationTask(rentalService);

        task.executeReconciliation();

        then(rentalService).should().processExpiredHolds();
        then(rentalService).should().processExpiredRentalsToPenalty();
    }

    @Test
    @DisplayName("executeReconciliation - swallows job exceptions to keep scheduler alive")
    void shouldKeepSchedulerAliveWhenServiceFails() {
        RentalService rentalService = mock(RentalService.class);
        given(rentalService.processExpiredHolds()).willThrow(new RuntimeException("database down"));
        HoldExpirationTask task = new HoldExpirationTask(rentalService);

        task.executeReconciliation();

        then(rentalService).should().processExpiredHolds();
    }
}
