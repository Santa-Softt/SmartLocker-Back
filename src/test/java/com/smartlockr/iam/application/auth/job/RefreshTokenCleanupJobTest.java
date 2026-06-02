package com.smartlockr.iam.application.auth.job;

import com.smartlockr.iam.application.auth.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    private RefreshTokenCleanupJob job;

    @Test
    @DisplayName("runDailyTokenCleanup - delega en RefreshTokenService.deleteExpiredTokens")
    void shouldInvokeServiceCleanup() {
        job = new RefreshTokenCleanupJob(refreshTokenService);

        job.runDailyTokenCleanup();

        verify(refreshTokenService).deleteExpiredTokens();
    }

    @Test
    @DisplayName("runDailyTokenCleanup - captura excepciones para no detener el scheduler")
    void shouldSwallowExceptionsToProtectScheduler() {
        job = new RefreshTokenCleanupJob(refreshTokenService);
        doThrow(new RuntimeException("DB connection lost"))
                .when(refreshTokenService).deleteExpiredTokens();

        assertThatCode(() -> job.runDailyTokenCleanup()).doesNotThrowAnyException();

        verify(refreshTokenService).deleteExpiredTokens();
    }
}
