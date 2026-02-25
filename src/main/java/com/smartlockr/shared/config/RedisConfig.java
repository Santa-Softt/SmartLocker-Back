package com.smartlockr.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

 
/**
 * Redis-related Spring configuration.
 * Registers beans required to listen to Redis key expiration events.
 * This configuration is disabled for the "test" profile.
 */
@Slf4j
@Configuration
@Profile("!test")
public class RedisConfig {

 
    /**
     * Creates the Redis message listener container used to receive keyspace notifications.
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
     * @param listenerContainer the container used to subscribe to Redis events
     * @return {@link KeyExpirationEventMessageListener} instance
     */
    @Bean
    public KeyExpirationEventMessageListener keyExpirationEventMessageListener(RedisMessageListenerContainer listenerContainer) {
        return new KeyExpirationEventMessageListener(listenerContainer);
    }
}
