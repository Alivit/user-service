package com.minispring.userservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
@EnableAsync
public class RedisConfig {

    @Value("${app.cache.lifecycle.default:1h}")
    private Duration defaultValue;

    @Value("${app.cache.lifecycle.user-info:24h}")
    private Duration userInfo;

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType("com.minispring.userservice.dto")
                .allowIfBaseType("java.util")
                .allowIfBaseType("java.lang")
                .allowIfBaseType("java.time")
                .build();

        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .customize(builder -> builder.findAndAddModules()
                        .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY))
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultValue)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            RedisCacheConfiguration defaultCacheConfig) {
        return (builder) ->
                builder.transactionAware().withCacheConfiguration("user_info", defaultCacheConfig.entryTtl(userInfo));
    }
}
