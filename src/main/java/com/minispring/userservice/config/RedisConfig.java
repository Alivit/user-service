package com.minispring.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${app.cache.lifecycle.default:1h}")
    private Duration defaultValue;

    @Value("${app.cache.lifecycle.user-info:24h}")
    private Duration userInfo;

    @Value("${app.cache.lifecycle.card-info:6h}")
    private Duration cardInfo;

    @Value("${app.cache.lifecycle.user-cards:12h}")
    private Duration userCards;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory){
        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        RedisCacheConfiguration redisConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(defaultValue)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
        Map<String, RedisCacheConfiguration> cacheStorage = new HashMap<>();
        cacheStorage.put("user_info", redisConfig.entryTtl(userInfo));
        cacheStorage.put("card_info", redisConfig.entryTtl(cardInfo));
        cacheStorage.put("user_cards", redisConfig.entryTtl(userCards));
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisConfig)
                .withInitialCacheConfigurations(cacheStorage)
                .build();

        return new TransactionAwareCacheManagerProxy(redisCacheManager);
    }

}
