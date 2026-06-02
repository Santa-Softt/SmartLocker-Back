package com.smartlockr.shared;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @MockitoBean
    protected RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setupRedisMock() {
        RedisConnection mockConnection = Mockito.mock(RedisConnection.class);
        RedisServerCommands mockServerCommands = Mockito.mock(RedisServerCommands.class);

        Mockito.when(redisConnectionFactory.getConnection()).thenReturn(mockConnection);

        Mockito.when(mockConnection.serverCommands()).thenReturn(mockServerCommands);
    }
}
