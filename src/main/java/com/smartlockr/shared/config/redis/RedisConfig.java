package com.smartlockr.shared.config.redis;

import com.smartlockr.shared.properties.RedisProperties;
import com.smartlockr.shared.utils.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Map;

/**
 * Configuration class for Redis distributed caching and keyspace event listeners.
 * Implements an isolated ObjectMapper with a custom TypeResolverBuilder to safely serialize
 * Java Records without relying on the deprecated EVERYTHING typing strategy.
 * Enums are explicitly excluded from polymorphic serialization to prevent structural mismatches
 * and optimize storage footprint, relying on POJO signatures for deserialization.
 * * Disabled during test profiles to avoid requiring a running Redis instance for unit tests.
 */
@Configuration
@Profile("!test")
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties properties;
    private final RedisSerializer<Object> redisSerializer;

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> {
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(properties.cache().defaultTtl())
                    .disableCachingNullValues()
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer));

            builder.cacheDefaults(defaultConfig);

            builder.withInitialCacheConfigurations(Map.of(
                    CacheNames.USER_CACHE, defaultConfig.entryTtl(properties.cache().userTtl()),
                    CacheNames.LOCKER_SUMMARY_CACHE, defaultConfig.entryTtl(properties.cache().lockerSummary()),
                    CacheNames.LOCKER_AVAILABLE_BY_SIZE_CACHE, defaultConfig.entryTtl(properties.cache().lockerAvailableBySize())
            ));
        };
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public KeyExpirationEventMessageListener keyExpirationEventMessageListener(
            RedisMessageListenerContainer redisMessageListenerContainer) {
        return new KeyExpirationEventMessageListener(redisMessageListenerContainer);
    }
}