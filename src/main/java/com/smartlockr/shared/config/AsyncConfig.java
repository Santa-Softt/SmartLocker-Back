package com.smartlockr.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for handling background tasks like webhook processing.
 * Configures a dedicated thread pool for webhook processing to avoid blocking
 * HTTP responses while ensuring reliable payment processing.
 * All pool settings are configurable via application properties.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Value("${async.webhook.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.webhook.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${async.webhook.queue-capacity:100}")
    private int queueCapacity;

    @Value("${async.webhook.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    /**
     * Creates a dedicated executor for webhook processing with configurable settings.
     * Core pool handles normal load, max pool handles traffic spikes,
     * and queue capacity prevents memory exhaustion during bursts.
     * Rejected tasks run in the caller's thread to prevent data loss.
     *
     * @return configured ThreadPoolTaskExecutor for webhook processing
     */
    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("webhook-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        log.info("Webhook async executor configured: core={}, max={}, queue={}, await={}s",
                corePoolSize, maxPoolSize, queueCapacity, awaitTerminationSeconds);
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return webhookExecutor();
    }
}
