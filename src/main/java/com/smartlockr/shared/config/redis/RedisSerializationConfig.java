package com.smartlockr.shared.config.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisSerializationConfig {

    private static final String DOMAIN_BASE_PACKAGE = "com.smartlockr.";

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        ObjectMapper mapper = createRedisObjectMapper();
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    private ObjectMapper createRedisObjectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(DOMAIN_BASE_PACKAGE)
                .build();

        ObjectMapper.DefaultTypeResolverBuilder typer = new ObjectMapper.DefaultTypeResolverBuilder(
                ObjectMapper.DefaultTyping.NON_FINAL, ptv) {
            @Override
            public boolean useForType(JavaType t) {
                if (t.isEnumType() || t.isPrimitive()) {
                    return false;
                }
                return t.getRawClass().getName().startsWith(DOMAIN_BASE_PACKAGE);
            }
        };

        typer.init(JsonTypeInfo.Id.CLASS, null);
        typer.inclusion(JsonTypeInfo.As.PROPERTY);

        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultTyping(typer)
                .build();
    }
}
