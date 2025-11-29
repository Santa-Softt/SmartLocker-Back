package com.smartlockr.iam.application.auth.job;

import com.smartlockr.iam.application.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    /**
     * Cron Expression: "0 0 3 * * *"
     * Se ejecuta a las 03:00 AM todos los días.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDailyTokenCleanup() {
        log.info("Starting scheduled job: Expired Token Cleanup");
        try {
            refreshTokenService.deleteExpiredTokens();
            log.info("Completed scheduled job: Expired Token Cleanup");
        } catch (Exception e) {
            // Importante capturar excepciones para no detener el thread del scheduler si hay otros jobs
            log.error("Failed to execute token cleanup job", e);
        }
    }
}
