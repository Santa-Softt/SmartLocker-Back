package com.smartlockr.shared.config;

import com.smartlockr.shared.utils.CacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;


/**
 * Redis-related Spring configuration.
 * Registers beans required to listen to Redis key expiration events.
 * This configuration is disabled for the "test" profile.
 * Redis is REQUIRED for:
 * - Real-time expiration notifications via keyspace events
 * - Distributed caching (BusinessConfig, etc.)
 * - Idempotency controls for payment processing
 * If Redis becomes unavailable at runtime, the system gracefully degrades
 * to database-based reconciliation with more frequent polling (2s interval).
 */
@Slf4j
@Configuration
@Profile("!test")
@EnableCaching
public class RedisConfig {

    /**
     * Creates the Redis message listener container used to receive keyspace notifications.
     *
     * @param connectionFactory Redis connection factory
     * @return configured {@link RedisMessageListenerContainer}
     */
    @Bean
    public RedisMessageListenerContainer keyExpirationListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        return listenerContainer;
    }

    /**
     * Registers a listener that receives Redis key expiration events.
     *
     * @param listenerContainer the container used to subscribe to Redis events
     * @return {@link KeyExpirationEventMessageListener} instance
     */
    @Bean
    public KeyExpirationEventMessageListener keyExpirationEventMessageListener(
            RedisMessageListenerContainer listenerContainer) {
        return new KeyExpirationEventMessageListener(listenerContainer);
    }

    /**
     * Configures Redis as the Spring Cache provider.
     * Used by {@link org.springframework.cache.annotation.Cacheable} and
     * {@link org.springframework.cache.annotation.CacheEvict} annotations across the application.
     *
     * @param connectionFactory Redis connection factory
     * @return configured {@link RedisCacheManager}
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(CacheNames.BUSINESS_CONFIG_CACHE, config)
                .build();
    }
}
