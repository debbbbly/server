package com.debbly.server.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class RedisConfig(private val objectMapper: ObjectMapper) {

    @Bean
    fun redisObjectMapper(): ObjectMapper =
        objectMapper.copy()
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ParameterNamesModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Any::class.java)
                    .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )

    @Bean
    @Primary
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        @Qualifier("redisObjectMapper") redisObjectMapper: ObjectMapper
    ): RedisCacheManager {
        // Force Jackson to always treat the value as Object.class
        val serializer = Jackson2JsonRedisSerializer(redisObjectMapper, Any::class.java)

        val config = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(SerializationPair.fromSerializer(serializer))
            .entryTtl(Duration.ofMinutes(10))

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(config)
            .build()
    }

    @Bean
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        @Qualifier("redisObjectMapper") redisObjectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val serializer = Jackson2JsonRedisSerializer(redisObjectMapper, Any::class.java)
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(redisConnectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = serializer
        }
    }
}
